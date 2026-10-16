package org.koaks.codereview.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.rag.domain.RagChunk;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagItem;
import org.koaks.codereview.rag.mapper.RagChunkMapper;
import org.koaks.codereview.rag.mapper.RagItemMapper;
import org.koaks.codereview.scm.github.GitHubClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexer {
    private static final int MAX_TEXT = 60_000;
    private final RagItemMapper items;
    private final RagChunkMapper chunks;
    private final GitHubClient github;
    private final EmbeddingClient embeddings;
    private final MilvusVectorStore milvus;
    private final ElasticsearchBm25Store elasticsearch;

    public IndexedItem index(CodeRepository repo, RagEnums.ItemType type, String number) {
        if (repo.getExternalFullName() == null || repo.getCredentialId() == null) {
            throw new IllegalStateException("RAG requires a GitHub repository credential");
        }
        Snapshot snapshot = load(repo, type, number);
        RagItem item = items.selectOne(Wrappers.<RagItem>lambdaQuery()
                .eq(RagItem::getRepositoryId, repo.getId()).eq(RagItem::getItemType, type)
                .eq(RagItem::getItemNumber, number).last("LIMIT 1"));
        if (item == null) {
            item = new RagItem();
            item.setRepositoryId(repo.getId()); item.setItemType(type); item.setItemNumber(number);
        }
        item.setTitle(snapshot.title()); item.setBody(snapshot.body()); item.setState(normalizeState(snapshot.state()));
        item.setAuthorLogin(snapshot.author()); item.setHtmlUrl(snapshot.url());
        item.setSourceUpdatedAt(parseInstant(snapshot.updatedAt())); item.setContentHash(hash(snapshot.title() + "\n" + snapshot.body() + "\n" + snapshot.state()));
        item.setDeleted(0);
        if (item.getId() == null) items.insert(item); else items.updateById(item);

        List<RagChunk> desired = new ArrayList<>();
        desired.add(chunk(item, RagEnums.ChunkType.BODY, "BODY", null,
                prefix(type, number, snapshot.title(), snapshot.body()), item.getState()));
        for (GitHubClient.GitHubIssueComment comment : snapshot.comments()) {
            if (comment == null || !StringUtils.hasText(comment.body()) || comment.id() == null) continue;
            String author = comment.user() == null ? "unknown" : comment.user().login();
            desired.add(chunk(item, RagEnums.ChunkType.COMMENT, Long.toString(comment.id()), author,
                    prefix(type, number, "comment by @" + author, comment.body()), item.getState()));
        }
        List<RagChunk> existing = chunks.selectList(Wrappers.<RagChunk>lambdaQuery()
                .eq(RagChunk::getItemId, item.getId()));
        Set<String> desiredKeys = new HashSet<>(desired.stream().map(RagIndexer::chunkKey).toList());
        List<RagChunk> removed = existing.stream().filter(c -> !desiredKeys.contains(chunkKey(c))).toList();
        if (!removed.isEmpty()) { milvus.delete(removed); try { elasticsearch.delete(removed); } catch (RuntimeException e) { log.warn("RAG ES deletion failed", e); } }
        for (RagChunk old : existing) if (!desiredKeys.contains(chunkKey(old))) { old.setDeleted(1); chunks.updateById(old); }

        List<RagChunk> changed = new ArrayList<>();
        List<RagChunk> active = new ArrayList<>();
        for (RagChunk fresh : desired) {
            RagChunk old = existing.stream().filter(c -> chunkKey(c).equals(chunkKey(fresh))).findFirst().orElse(null);
            String oldHash = old == null ? null : old.getContentHash();
            if (old == null) { chunks.insert(fresh); old = fresh; }
            else {
                old.setTextContent(fresh.getTextContent()); old.setAuthorLogin(fresh.getAuthorLogin()); old.setState(fresh.getState());
                old.setContentHash(fresh.getContentHash()); old.setDeleted(0); chunks.updateById(old);
            }
            active.add(old);
            if (old.getIndexedAt() == null || oldHash == null || !oldHash.equals(fresh.getContentHash())) changed.add(old);
        }
        if (!changed.isEmpty()) {
            List<float[]> vectors = embeddings.embed(changed.stream().map(RagChunk::getTextContent).toList());
            milvus.upsert(changed, vectors);
            try { elasticsearch.upsert(changed); } catch (RuntimeException e) { log.warn("RAG ES indexing failed", e); }
            Instant now = Instant.now();
            for (RagChunk c : changed) { c.setIndexedAt(now); c.setEmbeddingVersion("qwen3.7-text-embedding"); chunks.updateById(c); }
        }
        item.setIndexedAt(Instant.now()); items.updateById(item);
        return new IndexedItem(item, active);
    }

    public void remove(CodeRepository repo, RagEnums.ItemType type, String number) {
        RagItem item = items.selectOne(Wrappers.<RagItem>lambdaQuery().eq(RagItem::getRepositoryId, repo.getId())
                .eq(RagItem::getItemType, type).eq(RagItem::getItemNumber, number).last("LIMIT 1"));
        if (item == null) return;
        List<RagChunk> existing = chunks.selectList(Wrappers.<RagChunk>lambdaQuery().eq(RagChunk::getItemId, item.getId()));
        if (!existing.isEmpty()) { try { milvus.delete(existing); } catch (RuntimeException e) { log.warn("RAG Milvus deletion failed", e); }
            try { elasticsearch.delete(existing); } catch (RuntimeException e) { log.warn("RAG ES deletion failed", e); } }
        existing.forEach(c -> { c.setDeleted(1); chunks.updateById(c); });
        item.setDeleted(1); items.updateById(item);
    }

    private Snapshot load(CodeRepository repo, RagEnums.ItemType type, String number) {
        if (type == RagEnums.ItemType.ISSUE) {
            GitHubClient.GitHubIssue issue = github.issue(repo.getExternalFullName(), number, repo.getCredentialId(), repo.getUserId());
            if (issue == null) throw new IllegalStateException("GitHub Issue was not found: #" + number);
            return new Snapshot(issue.title(), issue.body(), issue.state(), issue.user() == null ? null : issue.user().login(),
                    issue.htmlUrl(), issue.updatedAt(), github.issueComments(repo.getExternalFullName(), number, repo.getCredentialId(), repo.getUserId()));
        }
        GitHubClient.GitHubPullRequest pr = github.pullRequest(repo.getExternalFullName(), number, repo.getCredentialId(), repo.getUserId());
        if (pr == null) throw new IllegalStateException("GitHub PR was not found: #" + number);
        return new Snapshot(pr.title(), pr.body(), pr.state(), pr.user() == null ? null : pr.user().login(),
                pr.htmlUrl(), pr.updatedAt(), github.issueComments(repo.getExternalFullName(), number, repo.getCredentialId(), repo.getUserId()));
    }

    private static RagChunk chunk(RagItem item, RagEnums.ChunkType type, String commentId, String author, String text, String state) {
        RagChunk c = new RagChunk(); c.setItemId(item.getId()); c.setRepositoryId(item.getRepositoryId()); c.setItemType(item.getItemType());
        c.setItemNumber(item.getItemNumber()); c.setState(state); c.setChunkType(type); c.setExternalCommentId(commentId);
        c.setCommentKind(type == RagEnums.ChunkType.COMMENT ? "ISSUE_COMMENT" : null); c.setAuthorLogin(author);
        c.setTextContent(limit(text)); c.setContentHash(hash(c.getTextContent())); c.setDeleted(0); return c;
    }

    private static String prefix(RagEnums.ItemType type, String number, String title, String body) {
        return type.name() + " #" + number + "\n" + (title == null ? "" : title) + "\n\n" + (body == null ? "" : body);
    }
    private static String normalizeState(String value) { return "closed".equalsIgnoreCase(value) ? "closed" : "open"; }
    private static Instant parseInstant(String value) { try { return value == null ? null : Instant.parse(value); } catch (RuntimeException e) { return null; } }
    private static String limit(String value) { return value == null ? "" : value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT); }
    private static String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String chunkKey(RagChunk c) { return c.getChunkType() + ":" + c.getExternalCommentId(); }
    private record Snapshot(String title, String body, String state, String author, String url, String updatedAt,
                            List<GitHubClient.GitHubIssueComment> comments) {}
    public record IndexedItem(RagItem item, List<RagChunk> chunks) {}
}

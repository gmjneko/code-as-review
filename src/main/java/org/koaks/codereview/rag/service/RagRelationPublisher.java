package org.koaks.codereview.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.rag.mapper.RagRelationMapper;
import org.koaks.codereview.scm.github.GitHubClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 将数据库关系汇总写回 GitHub 双方对象。
 *
 * <p>评论正文包含固定 marker。再次发布时先按 marker 查找并更新原评论，从而保证 webhook
 * 重试、关系证据追加或服务重启不会不断创建重复评论。写回失败只更新发布状态，关系本身仍保留。</p>
 */
public class RagRelationPublisher {
    private final GitHubClient github;
    private final RagRelationMapper relations;

    public void publish(CodeRepository repo, RagRelation relation) {
        try {
            String sourceId = publishOne(repo, relation.getSourceType(), relation.getSourceNumber());
            String targetId = publishOne(repo, relation.getTargetType(), relation.getTargetNumber());
            relation.setSourceCommentId(sourceId); relation.setTargetCommentId(targetId);
            relation.setSourcePublishStatus(RagEnums.PublishStatus.PUBLISHED);
            relation.setTargetPublishStatus(RagEnums.PublishStatus.PUBLISHED);
            relation.setPublishError(null); relations.updateById(relation);
        } catch (RuntimeException e) {
            relation.setSourcePublishStatus(RagEnums.PublishStatus.FAILED);
            relation.setTargetPublishStatus(RagEnums.PublishStatus.FAILED);
            relation.setPublishError(e.getMessage()); relations.updateById(relation);
            log.warn("RAG relation {} writeback failed", relation.getId(), e);
        }
    }

    private String publishOne(CodeRepository repo, RagEnums.ItemType type, String number) {
        String marker = marker(repo.getId(), type, number);
        List<GitHubClient.GitHubIssueComment> comments = github.issueComments(repo.getExternalFullName(), number,
                repo.getCredentialId(), repo.getUserId());
        String body = render(repo, type, number);
        // marker 是稳定定位键；评论 ID 只用于 PATCH，不参与关系唯一性。
        GitHubClient.GitHubIssueComment existing = comments.stream()
                .filter(c -> c.body() != null && c.body().contains(marker)).findFirst().orElse(null);
        if (existing != null && existing.id() != null) {
            return github.updateIssueComment(repo.getExternalFullName(), existing.id(), body, repo.getCredentialId(), repo.getUserId());
        }
        return github.createIssueComment(repo.getExternalFullName(), number, body, repo.getCredentialId(), repo.getUserId());
    }

    private String render(CodeRepository repo, RagEnums.ItemType type, String number) {
        StringBuilder out = new StringBuilder(marker(repo.getId(), type, number))
                .append("\n## Code as Review 自动关联\n\n");
        List<RagRelation> rows = relations.selectList(Wrappers.<RagRelation>lambdaQuery()
                .eq(RagRelation::getRepositoryId, repo.getId())
                .and(w -> w.and(x -> x.eq(RagRelation::getSourceType, type).eq(RagRelation::getSourceNumber, number))
                        .or(x -> x.eq(RagRelation::getTargetType, type).eq(RagRelation::getTargetNumber, number)))
                .orderByDesc(RagRelation::getUpdatedAt));
        for (RagRelation row : rows) {
            boolean source = row.getSourceType() == type && row.getSourceNumber().equals(number);
            RagEnums.ItemType otherType = source ? row.getTargetType() : row.getSourceType();
            String otherNumber = source ? row.getTargetNumber() : row.getSourceNumber();
            out.append("- ").append(otherType == RagEnums.ItemType.PR ? "PR" : "Issue").append(" #")
                    .append(otherNumber).append(" — ").append(row.getRelationKind())
                    .append(" (confidence ").append(row.getConfidence()).append(")");
            if (row.getReason() != null && !row.getReason().isBlank()) out.append(" — ").append(row.getReason());
            if (row.getEvidenceJson() != null && !row.getEvidenceJson().isBlank()) out.append("\n  evidence: ").append(row.getEvidenceJson());
            out.append('\n');
        }
        if (rows.isEmpty()) out.append("（暂无自动关联）\n");
        return out.toString();
    }

    public static String marker(long repositoryId, RagEnums.ItemType type, String number) {
        return "<!-- code-as-review:rag:" + repositoryId + ":" + type + ":" + number + " -->";
    }
}

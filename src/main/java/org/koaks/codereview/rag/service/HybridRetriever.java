package org.koaks.codereview.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.rag.domain.RagEnums;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * Milvus + Elasticsearch 的混合检索器。
 *
 * <p>两路后端的原始分数不可直接比较：COSINE 是向量相似度，ES 分数是 BM25。因此这里
 * 只使用各自结果的排名，通过 Reciprocal Rank Fusion（RRF）合并，再按父 Issue/PR 去重。
 * 召回命中评论时，Candidate 仍然保留评论 ID 和作者，供关系证据使用。</p>
 */
public class HybridRetriever {
    private final EmbeddingClient embeddings;
    private final MilvusVectorStore milvus;
    private final ElasticsearchBm25Store elasticsearch;
    private final CodeReviewProperties properties;

    public List<Candidate> retrieve(long repositoryId, RagEnums.ItemType sourceType, String sourceNumber,
                                    String text, RagEnums.ItemType targetType, boolean prioritizeOpen) {
        if (text == null || text.isBlank()) return List.of();
        List<float[]> vectors = embeddings.embed(List.of(text));
        if (vectors.isEmpty()) return List.of();
        // Issue->Issue 先检索 open，再用 closed 补齐；Issue->PR/PR->Issue 不需要这个优先级。
        if (!prioritizeOpen) return combine(repositoryId, targetType, sourceNumber, text, vectors.getFirst(), null);
        List<Candidate> open = combine(repositoryId, targetType, sourceNumber, text, vectors.getFirst(), "open");
        if (open.size() >= properties.rag().retrieval().backendTopK()) return open;
        List<Candidate> closed = combine(repositoryId, targetType, sourceNumber, text, vectors.getFirst(), "closed");
        Map<String, Candidate> merged = new LinkedHashMap<>();
        open.forEach(c -> merged.put(c.key(), c));
        closed.forEach(c -> merged.putIfAbsent(c.key(), c));
        return merged.values().stream().limit(properties.rag().retrieval().backendTopK()).toList();
    }

    private List<Candidate> combine(long repositoryId, RagEnums.ItemType targetType, String excluded,
                                    String text, float[] vector, String state) {
        int k = properties.rag().retrieval().backendTopK();
        List<MilvusVectorStore.Hit> dense = milvus.search(vector, repositoryId, targetType, state, excluded, k);
        List<ElasticsearchBm25Store.Hit> lexical;
        try {
            lexical = elasticsearch.search(text, repositoryId, targetType, state, excluded, k);
        } catch (RuntimeException failure) {
            // ES 是增强项，故障时保留 Milvus 召回并记录日志；Milvus 故障则由调用方重试任务。
            lexical = List.of();
            log.warn("RAG Elasticsearch lookup failed; continuing with Milvus results", failure);
        }
        Map<String, Candidate> result = new LinkedHashMap<>();
        add(result, dense.stream().map(h -> new RawHit(h.chunkId(), h.itemNumber(), h.itemType(), h.chunkType(),
                h.externalCommentId(), h.authorLogin(), state, h.score())).toList(), "milvus");
        add(result, lexical.stream().map(h -> new RawHit(h.chunkId(), h.itemNumber(), h.itemType(), h.chunkType(),
                h.externalCommentId(), h.authorLogin(), state, h.score())).toList(), "elasticsearch");
        return result.values().stream().sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(k).toList();
    }

    private void add(Map<String, Candidate> result, List<RawHit> hits, String backend) {
        int rank = 1;
        double rrfK = Math.max(1, properties.rag().retrieval().rrfK());
        for (RawHit hit : hits) {
            String key = hit.itemType() + ":" + hit.itemNumber();
            // 同一个父对象可能有正文和多条评论命中，父对象只保留一条 Candidate。
            Candidate current = result.get(key);
            double score = 1d / (rrfK + rank++);
            if (current == null) {
                result.put(key, new Candidate(hit.itemType(), hit.itemNumber(), score, hit.state(),
                        hit.chunkType(), hit.externalCommentId(), hit.authorLogin(), backend));
            } else {
                result.put(key, current.withScore(current.score() + score));
            }
        }
    }

    private record RawHit(String chunkId, String itemNumber, String itemType, String chunkType,
                           String externalCommentId, String authorLogin, String state, double score) {}

    public record Candidate(String itemType, String itemNumber, double score, String state, String chunkType,
                             String externalCommentId, String authorLogin, String backend) {
        /** 父对象键用于跨检索后端、跨 chunk 去重。 */
        public String key() { return itemType + ":" + itemNumber; }
        Candidate withScore(double value) { return new Candidate(itemType, itemNumber, value, state, chunkType, externalCommentId, authorLogin, backend); }
    }
}

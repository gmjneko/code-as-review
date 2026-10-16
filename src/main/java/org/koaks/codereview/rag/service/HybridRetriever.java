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
        public String key() { return itemType + ":" + itemNumber; }
        Candidate withScore(double value) { return new Candidate(itemType, itemNumber, value, state, chunkType, externalCommentId, authorLogin, backend); }
    }
}

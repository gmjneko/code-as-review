package org.koaks.codereview.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.rag.domain.RagChunk;
import org.koaks.codereview.rag.domain.RagEnums;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBm25Store {
    private final CodeReviewProperties properties;
    private volatile ElasticsearchClient client;
    private volatile Rest5Client lowLevelClient;
    private volatile boolean ready;

    public boolean enabled() { return properties.rag().elasticsearch().enabled(); }

    public synchronized void upsert(List<RagChunk> chunks) {
        if (!enabled() || chunks.isEmpty()) return;
        ensureIndex();
        try {
            for (RagChunk c : chunks) client.index(i -> i.index(index()).id(id(c)).document(document(c)));
        } catch (Exception e) { throw new IllegalStateException("Elasticsearch indexing failed", e); }
    }

    public synchronized void delete(List<RagChunk> chunks) {
        if (!enabled() || chunks.isEmpty()) return;
        ensureIndex();
        try {
            for (RagChunk c : chunks) {
                try { client.delete(d -> d.index(index()).id(id(c))); }
                catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ignored) { }
            }
        } catch (Exception e) { throw new IllegalStateException("Elasticsearch deletion failed", e); }
    }

    public List<Hit> search(String text, long repositoryId, RagEnums.ItemType type, String state,
                            String excludedNumber, int topK) {
        if (!enabled()) return List.of();
        ensureIndex();
        try {
            List<Query> filters = new ArrayList<>();
            filters.add(Query.of(q -> q.term(t -> t.field("repository_id").value(repositoryId))));
            filters.add(Query.of(q -> q.term(t -> t.field("item_type").value(type.name()))));
            if (StringUtils.hasText(state)) filters.add(Query.of(q -> q.term(t -> t.field("state").value(state))));
            Query mustNot = StringUtils.hasText(excludedNumber)
                    ? Query.of(q -> q.term(t -> t.field("item_number").value(excludedNumber))) : null;
            Query query = Query.of(q -> q.bool(b -> {
                b.must(Query.of(x -> x.match(m -> m.field("content").query(text))));
                b.filter(filters);
                if (mustNot != null) b.mustNot(mustNot);
                return b;
            }));
            SearchResponse<Map> response = client.search(s -> s.index(index()).size(topK).query(query), Map.class);
            List<Hit> result = new ArrayList<>();
            for (co.elastic.clients.elasticsearch.core.search.Hit<Map> hit : response.hits().hits()) {
                Map<?, ?> source = hit.source() == null ? Map.of() : hit.source();
                result.add(new Hit(hit.id(), string(source.get("item_number")), string(source.get("item_type")),
                        string(source.get("chunk_type")), string(source.get("external_comment_id")),
                        string(source.get("author_login")), hit.score() == null ? 0d : hit.score()));
            }
            return result;
        } catch (Exception e) { throw new IllegalStateException("Elasticsearch search failed", e); }
    }

    public record Hit(String chunkId, String itemNumber, String itemType, String chunkType,
                      String externalCommentId, String authorLogin, double score) {}

    private synchronized void ensureIndex() {
        if (ready) return;
        try {
            if (!client.indices().exists(e -> e.index(index())).value()) {
                client.indices().create(c -> c.index(index()).mappings(mapping()));
            }
            ready = true;
        } catch (Exception e) { throw new IllegalStateException("Elasticsearch index initialization failed", e); }
    }

    private TypeMapping mapping() {
        return TypeMapping.of(m -> m
                .properties("repository_id", Property.of(p -> p.long_(l -> l)))
                .properties("item_type", Property.of(p -> p.keyword(k -> k)))
                .properties("item_number", Property.of(p -> p.keyword(k -> k)))
                .properties("state", Property.of(p -> p.keyword(k -> k)))
                .properties("chunk_type", Property.of(p -> p.keyword(k -> k)))
                .properties("external_comment_id", Property.of(p -> p.keyword(k -> k)))
                .properties("author_login", Property.of(p -> p.keyword(k -> k)))
                .properties("content", Property.of(p -> p.text(t -> t))));
    }

    private ElasticsearchClient client() {
        if (client != null) return client;
        synchronized (this) {
            if (client != null) return client;
            CodeReviewProperties.Elasticsearch cfg = properties.rag().elasticsearch();
            List<Header> headers = new ArrayList<>();
            if (StringUtils.hasText(cfg.username())) {
                String credentials = cfg.username() + ":" + (cfg.password() == null ? "" : cfg.password());
                headers.add(new BasicHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8))));
            }
            lowLevelClient = Rest5Client.builder(URI.create(cfg.url())).setDefaultHeaders(headers.toArray(Header[]::new)).build();
            client = new ElasticsearchClient(new Rest5ClientTransport(lowLevelClient, new Jackson3JsonpMapper()));
            return client;
        }
    }

    @PreDestroy
    public void close() {
        try { if (lowLevelClient != null) lowLevelClient.close(); }
        catch (Exception e) { log.debug("Failed to close Elasticsearch client", e); }
    }

    private Map<String, Object> document(RagChunk c) {
        Map<String, Object> value = new HashMap<>();
        value.put("repository_id", c.getRepositoryId()); value.put("item_type", c.getItemType().name());
        value.put("item_number", c.getItemNumber()); value.put("state", c.getState());
        value.put("chunk_type", c.getChunkType().name()); value.put("external_comment_id", c.getExternalCommentId());
        value.put("author_login", c.getAuthorLogin()); value.put("content", c.getTextContent());
        return value;
    }

    private String index() { return properties.rag().elasticsearch().index(); }
    private static String id(RagChunk c) { return c.getRepositoryId() + ":" + c.getItemType() + ":" + c.getItemNumber() + ":" + c.getChunkType() + ":" + (c.getExternalCommentId() == null ? "body" : c.getExternalCommentId()); }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
}

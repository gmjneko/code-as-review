package org.koaks.codereview.rag.service;

import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.rag.domain.RagChunk;
import org.koaks.codereview.rag.domain.RagEnums;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MilvusVectorStore {
    private final CodeReviewProperties properties;
    private MilvusClientV2 client;
    private boolean ready;

    public synchronized void upsert(List<RagChunk> chunks, List<float[]> vectors) {
        if (chunks.isEmpty()) return;
        ensureReady();
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk c = chunks.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("chunk_id", key(c));
            row.addProperty("repository_id", c.getRepositoryId());
            row.addProperty("item_type", c.getItemType().name());
            row.addProperty("item_number", c.getItemNumber());
            row.addProperty("state", c.getState());
            row.addProperty("chunk_type", c.getChunkType().name());
            row.addProperty("external_comment_id", c.getExternalCommentId());
            row.addProperty("author_login", c.getAuthorLogin());
            row.add("embedding", new com.google.gson.Gson().toJsonTree(vectors.get(i)));
            rows.add(row);
        }
        var builder = UpsertReq.builder().collectionName(collection()).data(rows);
        if (StringUtils.hasText(database())) builder.databaseName(database());
        client.upsert(builder.build());
    }

    public synchronized void delete(List<RagChunk> chunks) {
        if (chunks.isEmpty()) return;
        ensureReady();
        var builder = DeleteReq.builder().collectionName(collection())
                .ids(chunks.stream().map(MilvusVectorStore::key).map(v -> (Object) v).toList());
        if (StringUtils.hasText(database())) builder.databaseName(database());
        client.delete(builder.build());
    }

    public synchronized List<Hit> search(float[] vector, long repositoryId, RagEnums.ItemType type,
                                          String state, String excludedNumber, int topK) {
        ensureReady();
        StringBuilder filter = new StringBuilder("repository_id == ").append(repositoryId)
                .append(" && item_type == '").append(type.name()).append("'");
        if (StringUtils.hasText(state)) filter.append(" && state == '").append(state).append("'");
        if (StringUtils.hasText(excludedNumber)) filter.append(" && item_number != '").append(escape(excludedNumber)).append("'");
        var builder = SearchReq.builder().collectionName(collection()).annsField("embedding")
                .metricType(IndexParam.MetricType.COSINE).topK(topK).filter(filter.toString())
                .outputFields(List.of("repository_id", "item_type", "item_number", "chunk_type",
                        "external_comment_id", "author_login", "state"))
                .data(List.of(new FloatVec(vector)));
        if (StringUtils.hasText(database())) builder.databaseName(database());
        SearchResp response = client.search(builder.build());
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (SearchResp.SearchResult row : response.getSearchResults().getFirst()) {
            Map<String, Object> entity = row.getEntity() == null ? Map.of() : row.getEntity();
            hits.add(new Hit(String.valueOf(row.getId()), number(entity, "item_number"),
                    String.valueOf(entity.getOrDefault("item_type", type.name())),
                    String.valueOf(entity.getOrDefault("chunk_type", "BODY")),
                    nullable(entity.get("external_comment_id")), nullable(entity.get("author_login")),
                    row.getScore() == null ? 0d : row.getScore()));
        }
        return hits;
    }

    public record Hit(String chunkId, String itemNumber, String itemType, String chunkType,
                      String externalCommentId, String authorLogin, double score) {}

    private void ensureReady() {
        if (ready) return;
        CodeReviewProperties.Milvus cfg = properties.rag().milvus();
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(cfg.uri());
        if (StringUtils.hasText(cfg.token())) builder.token(cfg.token());
        if (StringUtils.hasText(cfg.database())) builder.dbName(cfg.database());
        client = new MilvusClientV2(builder.build());
        String collection = collection();
        var hasCollection = io.milvus.v2.service.collection.request.HasCollectionReq.builder()
                .collectionName(collection);
        if (StringUtils.hasText(database())) hasCollection.databaseName(database());
        if (!Boolean.TRUE.equals(client.hasCollection(hasCollection.build()))) {
            List<CreateCollectionReq.FieldSchema> fields = List.of(
                    CreateCollectionReq.FieldSchema.builder().name("chunk_id").dataType(DataType.VarChar)
                            .maxLength(255).isPrimaryKey(true).autoID(false).build(),
                    CreateCollectionReq.FieldSchema.builder().name("repository_id").dataType(DataType.Int64).build(),
                    CreateCollectionReq.FieldSchema.builder().name("item_type").dataType(DataType.VarChar).maxLength(16).build(),
                    CreateCollectionReq.FieldSchema.builder().name("item_number").dataType(DataType.VarChar).maxLength(64).build(),
                    CreateCollectionReq.FieldSchema.builder().name("state").dataType(DataType.VarChar).maxLength(16).build(),
                    CreateCollectionReq.FieldSchema.builder().name("chunk_type").dataType(DataType.VarChar).maxLength(16).build(),
                    CreateCollectionReq.FieldSchema.builder().name("external_comment_id").dataType(DataType.VarChar).maxLength(64).isNullable(true).build(),
                    CreateCollectionReq.FieldSchema.builder().name("author_login").dataType(DataType.VarChar).maxLength(255).isNullable(true).build(),
                    CreateCollectionReq.FieldSchema.builder().name("embedding").dataType(DataType.FloatVector).dimension(properties.rag().embedding().dimension()).build());
            IndexParam index = new IndexParam.IndexParamBuilder().fieldName("embedding").indexName("embedding_hnsw")
                    .indexType(IndexParam.IndexType.HNSW).metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("M", 16, "efConstruction", 200)).build();
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .fieldSchemaList(fields).enableDynamicField(false).build();
            var request = CreateCollectionReq.builder().collectionName(collection)
                    .collectionSchema(schema).indexParam(index);
            if (StringUtils.hasText(database())) request.databaseName(database());
            client.createCollection(request.build());
        }
        var load =
                io.milvus.v2.service.collection.request.LoadCollectionReq.builder().collectionName(collection);
        if (StringUtils.hasText(database())) load.databaseName(database());
        client.loadCollection(load.build());
        ready = true;
    }

    private String collection() { return properties.rag().milvus().collection(); }
    private String database() { return properties.rag().milvus().database(); }
    private static String key(RagChunk c) { return c.getRepositoryId() + ":" + c.getItemType() + ":" + c.getItemNumber() + ":" + c.getChunkType() + ":" + (c.getExternalCommentId() == null ? "body" : c.getExternalCommentId()); }
    private static String escape(String value) { return value.replace("'", "\\'"); }
    private static String nullable(Object value) { return value == null || "null".equals(value) ? null : String.valueOf(value); }
    private static String number(Map<String, Object> entity, String key) { return nullable(entity.get(key)); }
}

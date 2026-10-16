package org.koaks.codereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("code-review")
public record CodeReviewProperties(
        Path workspaceRoot,
        List<Path> localRepoRoots,
        Executor executor,
        Review review,
        Sandbox sandbox,
        Rag rag,
        LlmDefault llmDefault,
        Security security) {

    @ConstructorBinding
    public CodeReviewProperties(Path workspaceRoot, List<Path> localRepoRoots, Executor executor,
                                Review review, Sandbox sandbox, Rag rag, LlmDefault llmDefault, Security security) {
        this.workspaceRoot = workspaceRoot;
        this.localRepoRoots = localRepoRoots;
        this.executor = executor;
        this.review = review;
        this.sandbox = sandbox;
        this.rag = rag == null ? Rag.defaults() : rag;
        this.llmDefault = llmDefault;
        this.security = security;
    }

    /** Backwards-compatible constructor for callers created before sandbox settings existed. */
    public CodeReviewProperties(Path workspaceRoot, List<Path> localRepoRoots, Executor executor,
                                Review review, LlmDefault llmDefault, Security security) {
        this(workspaceRoot, localRepoRoots, executor, review,
                new Sandbox("ubuntu:22.04", "/workspace", 2L * 1024 * 1024 * 1024, 2L,
                        "none", 120, 512 * 1024), Rag.defaults(), llmDefault, security);
    }

    public record Executor(int taskConcurrency, int queueCapacity) {
    }

    /**
     * @param maxPromptDiffTokens diffs beyond this budget are listed by name only and read on demand
     *                            through {@code read_file_diff}
     */
    public record Review(
            String defaultEffort,
            int maxPromptDiffTokens,
            int maxFileDiffTokens,
            long maxTaskTokens,
            int maxComments,
            int planLineThreshold,
            int maxIters,
            int gitTimeoutSeconds) {
    }

    public record Sandbox(
            String image,
            String workspaceRoot,
            Long memorySizeBytes,
            Long cpuCount,
            String network,
            int commandTimeoutSeconds,
            int maxOutputBytes) {
    }

    public record Rag(
            boolean enabled,
            Embedding embedding,
            Milvus milvus,
            Elasticsearch elasticsearch,
            Retrieval retrieval,
            Worker worker,
            Timeouts timeouts) {

        public static Rag defaults() {
            return new Rag(false, new Embedding("https://llm-2oj5be0xcuob8n58.cn-beijing.maas.aliyuncs.com/api/v1", "", "qwen3.7-text-embedding", 1024, 20),
                    new Milvus("http://124.220.39.95:19530", "", "", "code_review_rag_chunks"),
                    new Elasticsearch(false, "http://space-51q23auv.ap-nanjing.qcloudes.com", "", "", "code_review_rag_chunks"),
                    new Retrieval(20, 3, 60), new Worker(1, 20), new Timeouts(30, 10, 10, 120));
        }
    }

    public record Embedding(String baseUrl, String apiKey, String model, int dimension, int batchSize) {
    }

    public record Milvus(String uri, String token, String database, String collection) {
    }

    public record Elasticsearch(boolean enabled, String url, String username, String password, String index) {
    }

    public record Retrieval(int backendTopK, int verifyTopK, int rrfK) {
    }

    public record Worker(int concurrency, int queueCapacity) {
    }

    public record Timeouts(int embeddingSeconds, int milvusSeconds, int elasticsearchSeconds,
                           int verificationSeconds) {
    }

    public record LlmDefault(String baseUrl, String apiKey, String modelName) {

        public boolean configured() {
            return modelName != null && !modelName.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Security(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl, String credentialKey) {
    }

}

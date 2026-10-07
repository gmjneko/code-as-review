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
        LlmDefault llmDefault,
        Security security) {

    @ConstructorBinding
    public CodeReviewProperties(Path workspaceRoot, List<Path> localRepoRoots, Executor executor,
                                Review review, Sandbox sandbox, LlmDefault llmDefault, Security security) {
        this.workspaceRoot = workspaceRoot;
        this.localRepoRoots = localRepoRoots;
        this.executor = executor;
        this.review = review;
        this.sandbox = sandbox;
        this.llmDefault = llmDefault;
        this.security = security;
    }

    /** Backwards-compatible constructor for callers created before sandbox settings existed. */
    public CodeReviewProperties(Path workspaceRoot, List<Path> localRepoRoots, Executor executor,
                                Review review, LlmDefault llmDefault, Security security) {
        this(workspaceRoot, localRepoRoots, executor, review,
                new Sandbox("ubuntu:22.04", "/workspace", 2L * 1024 * 1024 * 1024, 2L,
                        "none", 120, 512 * 1024), llmDefault, security);
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

    public record LlmDefault(String baseUrl, String apiKey, String modelName) {

        public boolean configured() {
            return modelName != null && !modelName.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Security(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl, String credentialKey) {
    }

}

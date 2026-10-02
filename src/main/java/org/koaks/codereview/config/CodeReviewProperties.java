package org.koaks.codereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("code-review")
public record CodeReviewProperties(
        Path workspaceRoot,
        List<Path> localRepoRoots,
        Executor executor,
        Review review,
        LlmDefault llmDefault,
        Security security) {

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

    public record LlmDefault(String baseUrl, String apiKey, String modelName) {

        public boolean configured() {
            return modelName != null && !modelName.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Security(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl, String credentialKey) {
    }
}

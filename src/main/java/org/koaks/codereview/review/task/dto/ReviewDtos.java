package org.koaks.codereview.review.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.koaks.codereview.review.domain.ReviewComment;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewTask;

import java.time.Instant;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * @param baseRef required for {@code COMMIT_RANGE}: the branch or commit the change is based on
     * @param headRef required for {@code COMMIT_RANGE}: the branch or commit under review
     */
    public record Create(
            @NotNull Long repositoryId,
            @NotNull ReviewEnums.TargetType targetType,
            @Size(max = 255) String baseRef,
            @Size(max = 255) String headRef,
            ReviewEnums.Effort effort,
            @Size(max = 8000) String background,
            Long modelConfigId) {
    }

    public record TaskView(
            Long id,
            Long repositoryId,
            ReviewEnums.TargetType targetType,
            ReviewEnums.TriggerType triggerType,
            String baseRef,
            String headRef,
            String baseSha,
            String headSha,
            ReviewEnums.Effort effort,
            ReviewEnums.TaskStatus status,
            Integer filesChanged,
            Integer filesReviewed,
            Integer commentCount,
            Integer roundsCompleted,
            Long inputTokens,
            Long outputTokens,
            String summary,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {

        public static TaskView of(ReviewTask t) {
            return new TaskView(t.getId(), t.getRepositoryId(), t.getTargetType(), t.getTriggerType(), t.getBaseRef(),
                    t.getHeadRef(), t.getBaseSha(), t.getHeadSha(), t.getEffort(), t.getStatus(), t.getFilesChanged(),
                    t.getFilesReviewed(), t.getCommentCount(), t.getRoundsCompleted(), t.getInputTokens(),
                    t.getOutputTokens(), t.getSummary(), t.getErrorMessage(), t.getCreatedAt(), t.getStartedAt(),
                    t.getFinishedAt());
        }
    }

    public record CommentView(
            Long id,
            String filePath,
            Integer startLine,
            Integer endLine,
            String category,
            String severity,
            String content,
            String existingCode,
            String suggestionCode,
            Integer round,
            ReviewEnums.CommentStatus status,
            String filterReason) {

        public static CommentView of(ReviewComment c) {
            return new CommentView(c.getId(), c.getFilePath(), c.getStartLine(), c.getEndLine(), c.getCategory(),
                    c.getSeverity(), c.getContent(), c.getExistingCode(), c.getSuggestionCode(), c.getRound(),
                    c.getStatus(), c.getFilterReason());
        }
    }
}

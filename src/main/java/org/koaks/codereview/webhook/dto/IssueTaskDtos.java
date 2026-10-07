package org.koaks.codereview.webhook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.koaks.codereview.repo.domain.ExecutionMode;
import org.koaks.codereview.review.agent.IssueInvestigationReport;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;

import java.time.Instant;
import java.util.List;

public final class IssueTaskDtos {

    private IssueTaskDtos() {
    }

    public record Create(@NotNull Long repositoryId,
                         @NotNull
                         @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "must be a positive number")
                         String issueNumber,
                         ReviewEnums.Effort effort,
                         @Size(max = 8000) String background,
                         Long modelConfigId,
                         @Size(max = 128) String modelName) {
    }

    public record TaskView(Long id, Long repositoryId, String issueNumber, String command,
                           ReviewEnums.TriggerType triggerType, ExecutionMode executionMode,
                           String baseRef, String baseSha, ReviewEnums.Effort effort,
                           Long modelConfigId, String modelName,
                           String status, String summary, String errorMessage,
                           Long inputTokens, Long outputTokens, String executionLog,
                           String externalCommentId, Instant createdAt, Instant startedAt,
                           Instant finishedAt) {
        public static TaskView of(IssueInvestigationTask task) {
            return new TaskView(task.getId(), task.getRepositoryId(), task.getIssueNumber(), task.getCommand(),
                    task.getTriggerType(), task.getExecutionMode(), task.getBaseRef(), task.getBaseSha(),
                    task.getEffort(), task.getModelConfigId(), task.getModelName(), task.getStatus().name(),
                    task.getSummary(), task.getErrorMessage(), task.getInputTokens(), task.getOutputTokens(),
                    task.getExecutionLog(), task.getExternalCommentId(), task.getCreatedAt(),
                    task.getStartedAt(), task.getFinishedAt());
        }
    }

    public record ReportView(String reproductionStatus, String summary, List<String> reproductionSteps,
                             List<String> observations, String rootCause, String suggestedFix,
                             String markdown) {
        public static ReportView of(IssueInvestigationReport report, String markdown) {
            return new ReportView(report.reproductionStatus(), report.summary(), report.reproductionSteps(),
                    report.observations(), report.rootCause(), report.suggestedFix(), markdown);
        }
    }
}

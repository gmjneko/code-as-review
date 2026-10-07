package org.koaks.codereview.webhook.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;
import org.koaks.codereview.repo.domain.ExecutionMode;
import org.koaks.codereview.review.domain.ReviewEnums;

import java.time.Instant;

@Getter
@Setter
@TableName("issue_investigation_task")
public class IssueInvestigationTask extends BaseEntity {

    private Long repositoryId;
    private Long userId;
    private String issueNumber;
    private Long webhookEventId;
    private String command;
    private ReviewEnums.TriggerType triggerType;
    private ExecutionMode executionMode;
    private String baseRef;
    private String baseSha;
    private ReviewEnums.Effort effort;
    private String background;
    private Long modelConfigId;
    private String modelName;
    private WebhookEnums.IssueTaskStatus status;
    private Long inputTokens;
    private Long outputTokens;
    private String summary;
    private String reportJson;
    private String reportMarkdown;
    private String executionLog;
    private String errorMessage;
    private String externalCommentId;
    private Instant startedAt;
    private Instant finishedAt;
}

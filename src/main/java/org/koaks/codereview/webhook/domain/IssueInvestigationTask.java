package org.koaks.codereview.webhook.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@TableName("issue_investigation_task")
public class IssueInvestigationTask extends BaseEntity {

    private Long repositoryId;
    private Long userId;
    private String issueNumber;
    private Long webhookEventId;
    private String command;
    private WebhookEnums.IssueTaskStatus status;
    private String errorMessage;
}

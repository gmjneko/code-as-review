package org.koaks.codereview.webhook.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;
import org.koaks.codereview.review.domain.ReviewEnums;

@Getter
@Setter
@TableName("repository_trigger_rule")
public class RepositoryTriggerRule extends BaseEntity {

    private Long repositoryId;
    private WebhookEnums.EventKind eventKind;
    private String action;
    private WebhookEnums.Mode mode;
    private String command;
    private Boolean enabled;
    private ReviewEnums.Effort effort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long modelConfigId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String modelName;
}

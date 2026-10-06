package org.koaks.codereview.webhook.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@TableName("webhook_event")
public class WebhookEvent extends BaseEntity {

    private String provider;
    private String deliveryId;
    private String eventType;
    private Long repositoryId;
    private String payload;
    private WebhookEnums.EventStatus status;
    private Long taskId;
}

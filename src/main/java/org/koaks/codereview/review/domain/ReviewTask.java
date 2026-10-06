package org.koaks.codereview.review.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("review_task")
public class ReviewTask extends BaseEntity {

    private Long userId;
    private Long repositoryId;
    private ReviewEnums.TargetType targetType;
    private ReviewEnums.TriggerType triggerType;
    private String baseRef;
    private String headRef;
    private String baseSha;
    private String headSha;
    private String externalRef;
    private String triggerKey;
    private ReviewEnums.Effort effort;
    private String background;
    private Long modelConfigId;
    private String modelName;
    private ReviewEnums.TaskStatus status;
    private Integer filesChanged;
    private Integer filesReviewed;
    private Integer commentCount;
    private Long inputTokens;
    private Long outputTokens;
    private Integer roundsCompleted;
    private String planResult;
    private String summary;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

}

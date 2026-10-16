package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("rag_task")
public class RagTask extends BaseEntity {
    private Long repositoryId;
    private Long userId;
    private RagEnums.TaskType taskType;
    private RagEnums.ItemType itemType;
    private String itemNumber;
    private Long webhookEventId;
    private String dedupeKey;
    private RagEnums.TaskStatus status;
    private Integer itemsTotal;
    private Integer itemsDone;
    private Integer chunksDone;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
}

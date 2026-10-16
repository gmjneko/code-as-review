package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("rag_task")
/**
 * RAG 异步任务记录。
 *
 * <p>队列本身在 JVM 内存中，数据库记录用于幂等去重、进度查询和服务重启恢复。Webhook
 * 任务通过 webhookEventId 与原始事件建立追踪关系。</p>
 */
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

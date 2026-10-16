package org.koaks.codereview.rag.dto;

import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.rag.domain.RagTask;

import java.time.Instant;

/** 对外返回的 RAG 任务和关系视图，避免直接暴露 MyBatis 实体。 */
public final class RagDtos {
    private RagDtos() {}
    /** 前端轮询同步任务时需要的最小状态集合。 */
    public record TaskView(Long id, Long repositoryId, RagEnums.TaskType taskType, RagEnums.TaskStatus status,
                           Integer itemsTotal, Integer itemsDone, Integer chunksDone, String errorMessage,
                           Instant createdAt, Instant finishedAt) {
        public static TaskView of(RagTask t) { return new TaskView(t.getId(), t.getRepositoryId(), t.getTaskType(), t.getStatus(),
                t.getItemsTotal(), t.getItemsDone(), t.getChunksDone(), t.getErrorMessage(), t.getCreatedAt(), t.getFinishedAt()); }
    }
    /** 关系中的枚举以字符串返回，减少前端对 Java 类型的耦合。 */
    public record RelationView(Long id, String sourceType, String sourceNumber, String targetType, String targetNumber,
                               String relationKind, String origin, java.math.BigDecimal confidence, String reason,
                               String evidenceJson) {
        public static RelationView of(RagRelation r) { return new RelationView(r.getId(), r.getSourceType().name(), r.getSourceNumber(),
                r.getTargetType().name(), r.getTargetNumber(), r.getRelationKind().name(), r.getOrigin().name(), r.getConfidence(),
                r.getReason(), r.getEvidenceJson()); }
    }
}

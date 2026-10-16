package org.koaks.codereview.rag.dto;

import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.rag.domain.RagTask;

import java.time.Instant;

public final class RagDtos {
    private RagDtos() {}
    public record TaskView(Long id, Long repositoryId, RagEnums.TaskType taskType, RagEnums.TaskStatus status,
                           Integer itemsTotal, Integer itemsDone, Integer chunksDone, String errorMessage,
                           Instant createdAt, Instant finishedAt) {
        public static TaskView of(RagTask t) { return new TaskView(t.getId(), t.getRepositoryId(), t.getTaskType(), t.getStatus(),
                t.getItemsTotal(), t.getItemsDone(), t.getChunksDone(), t.getErrorMessage(), t.getCreatedAt(), t.getFinishedAt()); }
    }
    public record RelationView(Long id, String sourceType, String sourceNumber, String targetType, String targetNumber,
                               String relationKind, String origin, java.math.BigDecimal confidence, String reason,
                               String evidenceJson) {
        public static RelationView of(RagRelation r) { return new RelationView(r.getId(), r.getSourceType().name(), r.getSourceNumber(),
                r.getTargetType().name(), r.getTargetNumber(), r.getRelationKind().name(), r.getOrigin().name(), r.getConfidence(),
                r.getReason(), r.getEvidenceJson()); }
    }
}

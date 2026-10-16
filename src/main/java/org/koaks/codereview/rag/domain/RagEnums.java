package org.koaks.codereview.rag.domain;

public final class RagEnums {
    private RagEnums() {}

    public enum ItemType { ISSUE, PR }
    public enum ChunkType { BODY, COMMENT }
    public enum TaskType { FULL_SYNC, ITEM_SYNC, ITEM_DELETE }
    public enum TaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    public enum RelationKind { DUPLICATE_ISSUE, RELATED_PR, EXPLICIT_REFERENCE }
    public enum RelationOrigin { RAG, EXPLICIT_REFERENCE }
    public enum PublishStatus { PENDING, PUBLISHED, FAILED }
}

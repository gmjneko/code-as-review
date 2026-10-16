package org.koaks.codereview.rag.domain;

/** RAG 模块使用的枚举集中定义，保证数据库值、索引值和 Java 值保持一致。 */
public final class RagEnums {
    private RagEnums() {}

    /** GitHub 可被关联的两类父对象。 */
    public enum ItemType { ISSUE, PR }
    /** 正文和普通 Issue 评论使用不同的 chunk 类型，便于保留证据来源。 */
    public enum ChunkType { BODY, COMMENT }
    /** 全量、单条增量和删除任务。 */
    public enum TaskType { FULL_SYNC, ITEM_SYNC, ITEM_DELETE }
    public enum TaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
    /** 关系语义；ISSUE-ISSUE 只允许 DUPLICATE_ISSUE。 */
    public enum RelationKind { DUPLICATE_ISSUE, RELATED_PR, EXPLICIT_REFERENCE }
    /** 关系来自显式引用解析还是混合检索与 Agent 核验。 */
    public enum RelationOrigin { RAG, EXPLICIT_REFERENCE }
    /** 双方汇总评论的写回状态。 */
    public enum PublishStatus { PENDING, PUBLISHED, FAILED }
}

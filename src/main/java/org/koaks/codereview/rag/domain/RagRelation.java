package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("rag_relation")
/**
 * 两个同仓库 Issue/PR 之间的规范化关系。
 *
 * <p>source/target 会在写入前排序规范化，因此 ISSUE #1 -> PR #2 和 PR #2 -> ISSUE #1
 * 只会有一条数据库记录。evidenceJson 保存召回来源、评论 ID 和作者等可审计证据。</p>
 */
public class RagRelation extends BaseEntity {
    private Long repositoryId;
    private RagEnums.ItemType sourceType;
    private String sourceNumber;
    private RagEnums.ItemType targetType;
    private String targetNumber;
    private RagEnums.RelationKind relationKind;
    private RagEnums.RelationOrigin origin;
    private BigDecimal confidence;
    private String reason;
    @TableField("evidence_json")
    private String evidenceJson;
    private String sourceCommentId;
    private String targetCommentId;
    private RagEnums.PublishStatus sourcePublishStatus;
    private RagEnums.PublishStatus targetPublishStatus;
    private String publishError;
}

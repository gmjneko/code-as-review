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

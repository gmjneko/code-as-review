package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("rag_chunk")
/**
 * 可被 embedding 和 BM25 检索的最小文档单元。
 *
 * <p>一个 Issue/PR 正文对应一个 BODY chunk；每条普通评论对应一个 COMMENT chunk。chunk
 * 自己携带父对象编号和评论作者，因此命中评论时不需要再猜测证据属于哪个父对象。</p>
 */
public class RagChunk extends BaseEntity {
    private Long itemId;
    private Long repositoryId;
    private RagEnums.ItemType itemType;
    private String itemNumber;
    private String state;
    private RagEnums.ChunkType chunkType;
    private String externalCommentId;
    private String commentKind;
    private String authorLogin;
    @TableField("text_content")
    private String textContent;
    private String contentHash;
    private String embeddingVersion;
    private Instant indexedAt;
}

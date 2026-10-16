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

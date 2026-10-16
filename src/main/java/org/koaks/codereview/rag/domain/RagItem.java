package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("rag_item")
public class RagItem extends BaseEntity {
    private Long repositoryId;
    private RagEnums.ItemType itemType;
    private String itemNumber;
    private String title;
    private String body;
    private String state;
    private String authorLogin;
    private String htmlUrl;
    private Instant sourceUpdatedAt;
    private String contentHash;
    private Instant indexedAt;
}

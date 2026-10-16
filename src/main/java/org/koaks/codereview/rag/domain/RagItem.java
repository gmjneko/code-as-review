package org.koaks.codereview.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("rag_item")
/**
 * GitHub Issue/PR 的当前快照。
 *
 * <p>RAG 检索只保存 chunk，但 Agent 核验必须读取完整的原始对象，因此数据库同时保存
 * 标题、正文、状态和更新时间。repositoryId、itemType、itemNumber 是父对象的稳定业务键。</p>
 */
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

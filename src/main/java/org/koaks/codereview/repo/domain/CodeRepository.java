package org.koaks.codereview.repo.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

@Getter
@Setter
@TableName("code_repository")
public class CodeRepository extends BaseEntity {

    private Long userId;
    private String name;
    private SourceType sourceType;
    private String localPath;
    private String remoteUrl;
    private String externalFullName;
    private String defaultBranch;
    private Long credentialId;
    private String webhookSecretCipher;
    private Instant lastSyncedAt;
}

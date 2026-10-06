package org.koaks.codereview.scm.credential.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

import java.time.Instant;

/** Reserved for GitHub/GitLab: a PAT, GitHub App private key or OAuth token, encrypted at rest. */
@Getter
@Setter
@TableName("scm_credential")
public class ScmCredential extends BaseEntity {

    private Long userId;
    private String name;
    private String provider;
    private String authType;
    private String host;
    private String secretCipher;
    private Instant expiresAt;

}

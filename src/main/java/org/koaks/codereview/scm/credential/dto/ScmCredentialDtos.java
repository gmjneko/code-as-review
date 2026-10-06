package org.koaks.codereview.scm.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.koaks.codereview.scm.credential.domain.ScmCredential;

import java.time.Instant;

public final class ScmCredentialDtos {

    private ScmCredentialDtos() {
    }

    public record Create(@NotBlank @Size(max = 64) String name,
                         @NotBlank @Size(max = 256) String token,
                         @Size(max = 255) String host,
                         @Size(max = 500) String remark) {
    }

    public record Update(@Size(max = 64) String name,
                         @Size(max = 256) String token,
                         @Size(max = 255) String host,
                         @Size(max = 500) String remark) {
    }

    public record View(Long id, String name, String provider, String authType, String host,
                       String maskedToken, String remark, Instant expiresAt, Instant createdAt) {
        public static View of(ScmCredential credential, String maskedToken) {
            return new View(credential.getId(), credential.getName(), credential.getProvider(),
                    credential.getAuthType(), credential.getHost(), maskedToken, credential.getRemark(),
                    credential.getExpiresAt(), credential.getCreatedAt());
        }
    }
}

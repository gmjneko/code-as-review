package org.koaks.codereview.repo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;

import java.time.LocalDateTime;

public final class RepoDtos {

    private RepoDtos() {
    }

    public record Create(
            @NotBlank @Size(max = 128) String name,
            @NotNull SourceType sourceType,
            @Size(max = 1024) String localPath,
            @Size(max = 1024) String remoteUrl,
            @Size(max = 255) String defaultBranch,
            Long credentialId) {
    }

    public record Update(@Size(max = 128) String name, @Size(max = 255) String defaultBranch) {
    }

    public record View(
            Long id,
            String name,
            SourceType sourceType,
            String localPath,
            String remoteUrl,
            String externalFullName,
            String defaultBranch,
            Long credentialId,
            LocalDateTime lastSyncedAt,
            LocalDateTime createdAt) {

        public static View of(CodeRepository r) {
            return new View(r.getId(), r.getName(), r.getSourceType(), r.getLocalPath(), r.getRemoteUrl(),
                    r.getExternalFullName(), r.getDefaultBranch(), r.getCredentialId(), r.getLastSyncedAt(),
                    r.getCreatedAt());
        }
    }
}

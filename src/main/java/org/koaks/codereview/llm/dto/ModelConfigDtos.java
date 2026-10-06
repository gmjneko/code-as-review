package org.koaks.codereview.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ModelConfigDtos {

    private ModelConfigDtos() {
    }

    public record Create(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 512) @Pattern(regexp = "^https?://.+") String baseUrl,
            @NotBlank @Size(max = 128) String modelName,
            @NotBlank @Size(max = 512) String apiKey,
            boolean isDefault) {
    }

    /** Null fields are left unchanged; the API key is replaced only when provided. */
    public record Update(
            @Size(max = 64) String name,
            @Size(max = 512) @Pattern(regexp = "^https?://.+") String baseUrl,
            @Size(max = 128) String modelName,
            @Size(max = 512) String apiKey,
            Boolean isDefault) {
    }

    public record View(Long id, String name, String baseUrl, String modelName, String apiKeyMasked,
                       boolean isDefault, Instant createdAt) {
    }
}

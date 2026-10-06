package org.koaks.codereview.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ModelConfigDtos {

    private ModelConfigDtos() {
    }

    public record Limit(@NotNull @Positive Integer context,
                        @NotNull @Positive Integer output) {
    }

    public record Modalities(@NotEmpty List<@NotBlank String> input,
                             @JsonProperty("reasoning_effort") @NotEmpty
                             List<@Pattern(regexp = "low|high|max") String> reasoningEffort) {
    }

    public record Model(@NotNull @Valid Limit limit,
                        @NotNull @Valid Modalities modalities) {
    }

    public record Create(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 512) @Pattern(regexp = "^https?://.+") String baseUrl,
            @NotEmpty @Size(max = 32) Map<@NotBlank @Size(max = 128) String, @Valid Model> models,
            @NotBlank @Size(max = 512) String apiKey,
            boolean isDefault) {
    }

    /** Null fields are left unchanged; the API key is replaced only when provided. */
    public record Update(
            @Size(max = 64) String name,
            @Size(max = 512) @Pattern(regexp = "^https?://.+") String baseUrl,
            @Size(max = 32) Map<@NotBlank @Size(max = 128) String, @Valid Model> models,
            @Size(max = 512) String apiKey,
            Boolean isDefault) {
    }

    public record View(Long id, String name, String baseUrl, Map<String, Model> models, String apiKeyMasked,
                       boolean isDefault, Instant createdAt) {
    }

}

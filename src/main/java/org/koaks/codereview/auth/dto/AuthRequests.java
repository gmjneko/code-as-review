package org.koaks.codereview.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String username,
            @Email @Size(max = 128) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record Login(@NotBlank String username, @NotBlank String password) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }
}

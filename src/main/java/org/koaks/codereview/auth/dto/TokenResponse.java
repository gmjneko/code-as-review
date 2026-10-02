package org.koaks.codereview.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}

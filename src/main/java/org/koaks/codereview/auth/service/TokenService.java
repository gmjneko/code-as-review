package org.koaks.codereview.auth.service;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.dto.TokenResponse;
import org.koaks.codereview.config.CodeReviewProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues short-lived JWT access tokens and opaque refresh tokens. Refresh tokens live only in
 * Redis so they can be revoked (logout) and are rotated on every use.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String REFRESH_KEY_PREFIX = "cr:auth:refresh:";
    private static final String ISSUER = "code-as-review";

    private final JwtEncoder jwtEncoder;
    private final StringRedisTemplate redis;
    private final CodeReviewProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenResponse issue(long userId, String username) {
        Duration accessTtl = properties.security().accessTokenTtl();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("username", username)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(REFRESH_KEY_PREFIX + refreshToken, Long.toString(userId),
                properties.security().refreshTokenTtl());
        return new TokenResponse(accessToken, refreshToken, accessTtl.toSeconds());
    }

    /** Consumes the refresh token; a token can be exchanged exactly once. */
    public Optional<Long> consumeRefreshToken(String refreshToken) {
        String userId = redis.opsForValue().getAndDelete(REFRESH_KEY_PREFIX + refreshToken);
        return Optional.ofNullable(userId).map(Long::parseLong);
    }

    public void revokeRefreshToken(String refreshToken) {
        redis.delete(REFRESH_KEY_PREFIX + refreshToken);
    }
}

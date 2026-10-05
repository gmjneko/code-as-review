package org.koaks.codereview.auth.service;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.dto.AuthRequests;
import org.koaks.codereview.auth.dto.TokenResponse;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.user.domain.SystemUser;
import org.koaks.codereview.user.service.UserAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountService userAccounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public TokenResponse register(AuthRequests.Register request) {
        String email = StringUtils.hasText(request.email()) ? request.email() : null;
        try {
            SystemUser user = userAccounts.create(request.username(), email, passwordEncoder.encode(request.password()));
            return tokenService.issue(user.getId(), user.getUsername());
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("username or email already registered");
        }
    }

    public TokenResponse login(AuthRequests.Login request) {
        SystemUser user = userAccounts.findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("invalid username or password");
        }
        if (!SystemUser.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BizException.unauthorized("account disabled");
        }
        return tokenService.issue(user.getId(), user.getUsername());
    }

    public TokenResponse refresh(AuthRequests.Refresh request) {
        long userId = tokenService.consumeRefreshToken(request.refreshToken())
                .orElseThrow(() -> BizException.unauthorized("refresh token invalid or expired"));
        SystemUser user = userAccounts.requireActive(userId);
        return tokenService.issue(user.getId(), user.getUsername());
    }

    public void logout(AuthRequests.Refresh request) {
        tokenService.revokeRefreshToken(request.refreshToken());
    }

}

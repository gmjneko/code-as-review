package org.koaks.codereview.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.dto.AuthRequests;

import org.koaks.codereview.auth.dto.TokenResponse;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.user.SysUser;
import org.koaks.codereview.user.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public TokenResponse register(AuthRequests.Register request) {
        SysUser user = SysUser.builder()
                .username(request.username())
                .email(StringUtils.hasText(request.email()) ? request.email() : null)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(SysUser.STATUS_ACTIVE)
                .build();

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("username or email already registered");
        }
        return tokenService.issue(user.getId(), user.getUsername());
    }

    public TokenResponse login(AuthRequests.Login request) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username())
        );
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("invalid username or password");
        }
        if (!SysUser.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BizException.unauthorized("account disabled");
        }
        return tokenService.issue(user.getId(), user.getUsername());
    }

    public TokenResponse refresh(AuthRequests.Refresh request) {
        long userId = tokenService.consumeRefreshToken(request.refreshToken())
                .orElseThrow(() -> BizException.unauthorized("refresh token invalid or expired"));
        SysUser user = userMapper.selectById(userId);
        if (user == null || !SysUser.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BizException.unauthorized("account unavailable");
        }
        return tokenService.issue(user.getId(), user.getUsername());
    }

    public void logout(AuthRequests.Refresh request) {
        tokenService.revokeRefreshToken(request.refreshToken());
    }

}

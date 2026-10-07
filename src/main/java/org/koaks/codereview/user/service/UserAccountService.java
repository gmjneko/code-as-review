package org.koaks.codereview.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.user.domain.SystemUser;
import org.koaks.codereview.user.mapper.SystemUserMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final SystemUserMapper mapper;

    public SystemUser create(String username, String email, String passwordHash) {
        SystemUser user = SystemUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .status(SystemUser.STATUS_ACTIVE)
                .build();
        mapper.insert(user);

        return user;
    }

    public SystemUser findByUsername(String username) {
        return mapper.selectOne(
                Wrappers.<SystemUser>lambdaQuery().eq(SystemUser::getUsername, username)
        );
    }

    public SystemUser findById(long id) {
        return mapper.selectById(id);
    }

    public SystemUser requireActive(long id) {
        SystemUser user = findById(id);
        if (user == null || !SystemUser.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BizException.unauthorized("account unavailable");
        }
        return user;
    }

}

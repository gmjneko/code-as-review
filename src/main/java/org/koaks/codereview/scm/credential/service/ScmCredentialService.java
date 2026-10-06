package org.koaks.codereview.scm.credential.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.crypto.SecretCipher;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.scm.credential.domain.ScmCredential;
import org.koaks.codereview.scm.credential.dto.ScmCredentialDtos;
import org.koaks.codereview.scm.credential.mapper.ScmCredentialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScmCredentialService {

    private final ScmCredentialMapper mapper;
    private final SecretCipher cipher;

    @Transactional
    public ScmCredentialDtos.View create(long userId, ScmCredentialDtos.Create request) {
        ScmCredential credential = new ScmCredential();
        credential.setUserId(userId);
        credential.setName(request.name().strip());
        credential.setProvider("GITHUB");
        credential.setAuthType("PAT");
        credential.setHost(StringUtils.hasText(request.host()) ? request.host().strip() : "github.com");
        credential.setRemark(blankToNull(request.remark()));
        credential.setSecretCipher(cipher.encrypt(request.token()));
        mapper.insert(credential);
        return view(credential);
    }

    public List<ScmCredentialDtos.View> list(long userId) {
        return mapper.selectList(Wrappers.<ScmCredential>lambdaQuery()
                        .eq(ScmCredential::getUserId, userId)
                        .orderByDesc(ScmCredential::getId))
                .stream().map(this::view).toList();
    }

    @Transactional
    public ScmCredentialDtos.View update(long userId, long id, ScmCredentialDtos.Update request) {
        ScmCredential credential = getOwned(userId, id);
        if (StringUtils.hasText(request.name())) {
            credential.setName(request.name().strip());
        }
        if (StringUtils.hasText(request.host())) {
            credential.setHost(request.host().strip());
        }
        if (request.remark() != null) {
            credential.setRemark(blankToNull(request.remark()));
        }
        if (StringUtils.hasText(request.token())) {
            credential.setSecretCipher(cipher.encrypt(request.token()));
        }
        mapper.updateById(credential);
        return view(credential);
    }

    public void delete(long userId, long id) {
        mapper.deleteById(getOwned(userId, id).getId());
    }

    public ScmCredential getOwned(long userId, long id) {
        ScmCredential credential = mapper.selectById(id);
        if (credential == null || !Long.valueOf(userId).equals(credential.getUserId())) {
            throw BizException.notFound("SCM credential");
        }
        return credential;
    }

    public String token(ScmCredential credential) {
        return cipher.decrypt(credential.getSecretCipher());
    }

    private ScmCredentialDtos.View view(ScmCredential credential) {
        return ScmCredentialDtos.View.of(credential, SecretCipher.mask(token(credential)));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}

package org.koaks.codereview.repo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.mapper.CodeRepositoryMapper;
import org.koaks.codereview.repo.dto.RepoDtos;
import org.koaks.codereview.scm.ScmProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepoService {

    private final CodeRepositoryMapper mapper;
    private final ScmProviderRegistry providers;

    public CodeRepository create(long userId, RepoDtos.Create request) {
        CodeRepository repo = new CodeRepository();
        repo.setUserId(userId);
        repo.setName(request.name());
        repo.setSourceType(request.sourceType());
        repo.setLocalPath(request.localPath());
        repo.setRemoteUrl(request.remoteUrl());
        repo.setDefaultBranch(request.defaultBranch());
        repo.setCredentialId(request.credentialId());
        providers.get(request.sourceType()).validate(repo);
        mapper.insert(repo);
        return repo;
    }

    public List<CodeRepository> list(long userId) {
        return mapper.selectList(Wrappers.<CodeRepository>lambdaQuery()
                .eq(CodeRepository::getUserId, userId)
                .orderByDesc(CodeRepository::getId));
    }

    public CodeRepository getOwned(long userId, long id) {
        CodeRepository repo = mapper.selectById(id);
        if (repo == null || repo.getUserId() != userId) {
            throw BizException.notFound("repository");
        }
        return repo;
    }

    public CodeRepository update(long userId, long id, RepoDtos.Update request) {
        CodeRepository repo = getOwned(userId, id);
        if (StringUtils.hasText(request.name())) {
            repo.setName(request.name());
        }
        if (request.defaultBranch() != null) {
            repo.setDefaultBranch(request.defaultBranch());
        }
        mapper.updateById(repo);
        return repo;
    }

    public void delete(long userId, long id) {
        mapper.deleteById(getOwned(userId, id).getId());
    }
}

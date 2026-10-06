package org.koaks.codereview.webhook.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.crypto.SecretCipher;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.mapper.CodeRepositoryMapper;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;
import org.koaks.codereview.webhook.dto.TriggerRuleDtos;
import org.koaks.codereview.webhook.dto.WebhookDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class WebhookConfigService {

    private final RepoService repos;
    private final CodeRepositoryMapper repositoryMapper;
    private final SecretCipher cipher;
    private final TriggerRuleService rules;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public WebhookDtos.Config rotate(long userId, long repositoryId, String endpoint) {
        CodeRepository repo = repos.getOwned(userId, repositoryId);
        if (repo.getSourceType() != org.koaks.codereview.repo.domain.SourceType.GITHUB) {
            throw BizException.badRequest("Webhook configuration is only available for GitHub repositories");
        }
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        repo.setWebhookSecretCipher(cipher.encrypt(secret));
        repositoryMapper.update(Wrappers.<CodeRepository>lambdaUpdate()
                .eq(CodeRepository::getId, repositoryId)
                .set(CodeRepository::getWebhookSecretCipher, repo.getWebhookSecretCipher()));
        return new WebhookDtos.Config(true, endpoint, secret, rules.list(userId, repositoryId).stream()
                .map(TriggerRuleDtos.View::of).toList());
    }

    public WebhookDtos.Config get(long userId, long repositoryId, String endpoint) {
        CodeRepository repo = repos.getOwned(userId, repositoryId);
        return new WebhookDtos.Config(repo.getWebhookSecretCipher() != null, endpoint, null,
                rules.list(userId, repositoryId).stream().map(TriggerRuleDtos.View::of).toList());
    }
}

package org.koaks.codereview.webhook.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.llm.service.LlmModelConfigService;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;
import org.koaks.codereview.webhook.domain.WebhookEnums;
import org.koaks.codereview.webhook.dto.TriggerRuleDtos;
import org.koaks.codereview.webhook.mapper.RepositoryTriggerRuleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TriggerRuleService {

    private final RepositoryTriggerRuleMapper mapper;
    private final RepoService repos;
    private final LlmModelConfigService models;

    public List<RepositoryTriggerRule> list(long userId, long repositoryId) {
        repos.getOwned(userId, repositoryId);
        List<RepositoryTriggerRule> stored = stored(repositoryId);
        return stored.isEmpty() ? defaults(repositoryId) : stored;
    }

    @Transactional
    public List<RepositoryTriggerRule> replace(long userId, long repositoryId, TriggerRuleDtos.Replace request) {
        CodeRepository repository = repos.getOwned(userId, repositoryId);
        List<RepositoryTriggerRule> existing = stored(repositoryId);
        List<RepositoryTriggerRule> result = new ArrayList<>();
        for (TriggerRuleDtos.Rule dto : request.rules()) {
            RepositoryTriggerRule incoming = dto.toEntity(repositoryId);
            RepositoryTriggerRule match = existing.stream().filter(r -> sameKey(r, incoming)).findFirst().orElse(null);
            if (match == null) {
                if (incoming.getModelConfigId() != null) {
                    models.checkUsable(userId, incoming.getModelConfigId(), incoming.getModelName());
                }
                mapper.insert(incoming);
                result.add(incoming);
            } else {
                match.setCommand(incoming.getCommand());
                match.setEnabled(incoming.getEnabled());
                match.setEffort(incoming.getEffort());
                match.setModelConfigId(incoming.getModelConfigId());
                match.setModelName(incoming.getModelName());
                if (match.getModelConfigId() != null) {
                    models.checkUsable(userId, match.getModelConfigId(), match.getModelName());
                }
                mapper.updateById(match);
                result.add(match);
            }
        }
        for (RepositoryTriggerRule old : existing) {
            if (result.stream().noneMatch(r -> r.getId().equals(old.getId()))) {
                old.setEnabled(false);
                mapper.updateById(old);
            }
        }
        return result;
    }

    public RepositoryTriggerRule findEnabled(long repositoryId, WebhookEnums.EventKind kind, String action,
                                              WebhookEnums.Mode mode, String command) {
        return mapper.selectOne(Wrappers.<RepositoryTriggerRule>lambdaQuery()
                .eq(RepositoryTriggerRule::getRepositoryId, repositoryId)
                .eq(RepositoryTriggerRule::getEventKind, kind)
                .eq(RepositoryTriggerRule::getAction, action)
                .eq(RepositoryTriggerRule::getMode, mode)
                .eq(command != null, RepositoryTriggerRule::getCommand, command)
                .eq(RepositoryTriggerRule::getEnabled, true)
                .last("LIMIT 1"));
    }

    public boolean hasConfigured(long repositoryId) {
        return mapper.selectCount(Wrappers.<RepositoryTriggerRule>lambdaQuery()
                .eq(RepositoryTriggerRule::getRepositoryId, repositoryId)) > 0;
    }

    public List<RepositoryTriggerRule> defaults(long repositoryId) {
        return List.of(rule(repositoryId, WebhookEnums.EventKind.PULL_REQUEST, "opened", WebhookEnums.Mode.AUTO, null),
                rule(repositoryId, WebhookEnums.EventKind.PULL_REQUEST, "synchronize", WebhookEnums.Mode.AUTO, null),
                rule(repositoryId, WebhookEnums.EventKind.ISSUE, "opened", WebhookEnums.Mode.AUTO, null),
                rule(repositoryId, WebhookEnums.EventKind.PR_COMMENT, "created", WebhookEnums.Mode.COMMAND, "/review"),
                rule(repositoryId, WebhookEnums.EventKind.ISSUE_COMMENT, "created", WebhookEnums.Mode.COMMAND, "/review"));
    }

    private List<RepositoryTriggerRule> stored(long repositoryId) {
        return mapper.selectList(Wrappers.<RepositoryTriggerRule>lambdaQuery()
                .eq(RepositoryTriggerRule::getRepositoryId, repositoryId)
                .orderByAsc(RepositoryTriggerRule::getId));
    }

    private static RepositoryTriggerRule rule(long repositoryId, WebhookEnums.EventKind kind, String action,
                                               WebhookEnums.Mode mode, String command) {
        RepositoryTriggerRule r = new RepositoryTriggerRule();
        r.setRepositoryId(repositoryId);
        r.setEventKind(kind);
        r.setAction(action);
        r.setMode(mode);
        r.setCommand(command);
        r.setEnabled(true);
        r.setEffort(org.koaks.codereview.review.domain.ReviewEnums.Effort.MEDIUM);
        return r;
    }

    private static boolean sameKey(RepositoryTriggerRule a, RepositoryTriggerRule b) {
        return a.getEventKind() == b.getEventKind() && a.getMode() == b.getMode()
                && a.getAction().equals(b.getAction())
                && java.util.Objects.equals(a.getCommand(), b.getCommand());
    }
}

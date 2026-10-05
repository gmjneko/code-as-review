package org.koaks.codereview.llm.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.crypto.SecretCipher;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.llm.domain.LlmModelConfig;
import org.koaks.codereview.llm.domain.ModelEndpoint;
import org.koaks.codereview.llm.dto.ModelConfigDtos;
import org.koaks.codereview.llm.mapper.LlmModelConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmModelConfigService {

    private final LlmModelConfigMapper mapper;
    private final SecretCipher cipher;
    private final CodeReviewProperties properties;

    @Transactional
    public ModelConfigDtos.View create(long userId, ModelConfigDtos.Create request) {
        LlmModelConfig config = new LlmModelConfig();
        config.setUserId(userId);
        config.setName(request.name());
        config.setBaseUrl(request.baseUrl());
        config.setModelName(request.modelName());
        config.setApiKeyCipher(cipher.encrypt(request.apiKey()));
        config.setIsDefault(request.isDefault());
        if (request.isDefault()) {
            clearDefault(userId);
        }
        mapper.insert(config);
        return view(config);
    }

    public List<ModelConfigDtos.View> list(long userId) {
        return mapper.selectList(Wrappers.<LlmModelConfig>lambdaQuery()
                        .eq(LlmModelConfig::getUserId, userId)
                        .orderByDesc(LlmModelConfig::getId))
                .stream().map(this::view).toList();
    }

    @Transactional
    public ModelConfigDtos.View update(long userId, long id, ModelConfigDtos.Update request) {
        LlmModelConfig config = getOwned(userId, id);
        if (StringUtils.hasText(request.name())) {
            config.setName(request.name());
        }
        if (StringUtils.hasText(request.baseUrl())) {
            config.setBaseUrl(request.baseUrl());
        }
        if (StringUtils.hasText(request.modelName())) {
            config.setModelName(request.modelName());
        }
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyCipher(cipher.encrypt(request.apiKey()));
        }
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(userId);
        }
        if (request.isDefault() != null) {
            config.setIsDefault(request.isDefault());
        }
        mapper.updateById(config);
        return view(config);
    }

    public void delete(long userId, long id) {
        mapper.deleteById(getOwned(userId, id).getId());
    }

    /** Validates that {@code configId}, when given, belongs to the user. */
    public void checkUsable(long userId, Long configId) {
        if (configId != null) {
            getOwned(userId, configId);
        } else {
            resolve(userId, null);
        }
    }

    /** Explicit config, else the user's default, else the system default from configuration. */
    public ModelEndpoint resolve(long userId, Long configId) {
        LlmModelConfig config = configId != null
                ? getOwned(userId, configId)
                : mapper.selectOne(Wrappers.<LlmModelConfig>lambdaQuery()
                .eq(LlmModelConfig::getUserId, userId)
                .eq(LlmModelConfig::getIsDefault, true)
                .last("LIMIT 1"));
        if (config != null) {
            return new ModelEndpoint(config.getBaseUrl(), cipher.decrypt(config.getApiKeyCipher()), config.getModelName());
        }
        CodeReviewProperties.LlmDefault fallback = properties.llmDefault();
        if (fallback == null || !fallback.configured()) {
            throw BizException.badRequest("no model configured: add a model config or set code-review.llm-default");
        }
        return new ModelEndpoint(fallback.baseUrl(), fallback.apiKey(), fallback.modelName());
    }

    private LlmModelConfig getOwned(long userId, long id) {
        LlmModelConfig config = mapper.selectById(id);
        if (config == null || config.getUserId() != userId) {
            throw BizException.notFound("model config");
        }
        return config;
    }

    private void clearDefault(long userId) {
        mapper.update(Wrappers.<LlmModelConfig>lambdaUpdate()
                .eq(LlmModelConfig::getUserId, userId)
                .eq(LlmModelConfig::getIsDefault, true)
                .set(LlmModelConfig::getIsDefault, false));
    }

    private ModelConfigDtos.View view(LlmModelConfig c) {
        return new ModelConfigDtos.View(c.getId(), c.getName(), c.getBaseUrl(), c.getModelName(),
                SecretCipher.mask(cipher.decrypt(c.getApiKeyCipher())), Boolean.TRUE.equals(c.getIsDefault()),
                c.getCreatedAt());
    }
}

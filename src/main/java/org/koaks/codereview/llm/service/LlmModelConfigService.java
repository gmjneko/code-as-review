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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class LlmModelConfigService {

    private final LlmModelConfigMapper mapper;
    private final SecretCipher cipher;
    private final CodeReviewProperties properties;
    private final JsonMapper json;

    @Transactional
    public ModelConfigDtos.View create(long userId, ModelConfigDtos.Create request) {
        LlmModelConfig config = new LlmModelConfig();
        config.setUserId(userId);
        config.setName(request.name());
        config.setBaseUrl(request.baseUrl());
        config.setModelNamesJson(serializeModels(request.models()));
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
        if (StringUtils.hasText(request.apiKey())) {
            config.setApiKeyCipher(cipher.encrypt(request.apiKey()));
        }
        if (request.models() != null) {
            config.setModelNamesJson(serializeModels(request.models()));
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

    /**
     * Validates that {@code configId}, when given, belongs to the user.
     */
    public void checkUsable(long userId, Long configId) {
        checkUsable(userId, configId, null);
    }

    public void checkUsable(long userId, Long configId, String modelName) {
        if (configId != null) {
            LlmModelConfig config = getOwned(userId, configId);
            selectModel(config, modelName);
        } else {
            resolve(userId, null, modelName);
        }
    }

    /**
     * Explicit config, else the user's default, else the system default from configuration.
     */
    public ModelEndpoint resolve(long userId, Long configId) {
        return resolve(userId, configId, null);
    }

    public ModelEndpoint resolve(long userId, Long configId, String modelName) {
        LlmModelConfig config = configId != null
                ? getOwned(userId, configId)
                : mapper.selectOne(Wrappers.<LlmModelConfig>lambdaQuery()
                .eq(LlmModelConfig::getUserId, userId)
                .eq(LlmModelConfig::getIsDefault, true)
                .last("LIMIT 1"));
        if (config != null) {
            return new ModelEndpoint(config.getBaseUrl(), cipher.decrypt(config.getApiKeyCipher()),
                    selectModel(config, modelName));
        }
        CodeReviewProperties.LlmDefault fallback = properties.llmDefault();
        if (fallback == null || !fallback.configured()) {
            throw BizException.badRequest("no model configured: add a model config or set code-review.llm-default");
        }
        if (StringUtils.hasText(modelName) && !fallback.modelName().equals(modelName.strip())) {
            throw BizException.badRequest("model is not configured for the default model");
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
        Map<String, ModelConfigDtos.Model> models = parseModels(c.getModelNamesJson());
        return new ModelConfigDtos.View(c.getId(), c.getName(), c.getBaseUrl(), models,
                SecretCipher.mask(cipher.decrypt(c.getApiKeyCipher())), Boolean.TRUE.equals(c.getIsDefault()),
                c.getCreatedAt());
    }

    private String serializeModels(Map<String, ModelConfigDtos.Model> models) {
        if (models == null || models.isEmpty()) {
            throw BizException.badRequest("at least one model name is required");
        }
        Map<String, ModelConfigDtos.Model> normalized = new LinkedHashMap<>();
        models.forEach((name, model) -> {
            if (StringUtils.hasText(name) && model != null) {
                normalized.put(name.strip(), model);
            }
        });
        if (normalized.isEmpty()) {
            throw BizException.badRequest("at least one model name is required");
        }
        return json.writeValueAsString(normalized);
    }

    private String selectModel(LlmModelConfig config, String requestedModel) {
        Map<String, ModelConfigDtos.Model> models = parseModels(config.getModelNamesJson());
        List<String> names = List.copyOf(models.keySet());
        if (StringUtils.hasText(requestedModel)) {
            if (!names.contains(requestedModel.strip())) {
                throw BizException.badRequest("model is not configured for this model config");
            }
            return requestedModel.strip();
        }
        if (!names.isEmpty()) {
            return names.getFirst();
        }
        throw BizException.badRequest("no model configured for this model config");
    }

    private Map<String, ModelConfigDtos.Model> parseModels(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        JsonNode node = json.readTree(raw);
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, ModelConfigDtos.Model> models = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String name = field.getKey().strip();
            JsonNode model = field.getValue();
            JsonNode limit = model.path("limit");
            JsonNode modalities = model.path("modalities");
            List<String> input = StreamSupport.stream(modalities.path("input").spliterator(), false)
                    .map(JsonNode::asText).toList();
            List<String> reasoning = StreamSupport.stream(modalities.path("reasoning_effort").spliterator(), false)
                    .map(JsonNode::asText).map(value -> value.toLowerCase(Locale.ROOT)).toList();
            models.put(name, new ModelConfigDtos.Model(
                    new ModelConfigDtos.Limit(limit.path("context").asInt(), limit.path("output").asInt()),
                    new ModelConfigDtos.Modalities(input, reasoning)));
        }
        return models;
    }

}

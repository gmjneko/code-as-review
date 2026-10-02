package org.koaks.codereview.llm;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatModelFactory {

    public Model create(ModelEndpoint endpoint) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(endpoint.apiKey())
                .modelName(endpoint.modelName());
        if (StringUtils.hasText(endpoint.baseUrl())) {
            builder.baseUrl(endpoint.baseUrl());
        }
        return builder.build();
    }
}

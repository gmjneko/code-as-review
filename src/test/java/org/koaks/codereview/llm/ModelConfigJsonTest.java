package org.koaks.codereview.llm;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.llm.dto.ModelConfigDtos;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ModelConfigJsonTest {

    @Test
    void modelCapabilitiesRoundTripWithTheRequestedJsonShape() {
        JsonMapper json = JsonMapper.builder().build();
        String payload = """
                {
                  "name": "Platform",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "test-key",
                  "isDefault": true,
                  "models": {
                    "glm-5.3": {
                      "limit": {"context": 1024000, "output": 131072},
                      "modalities": {"input": ["text"], "reasoning_effort": ["low", "high", "max"]}
                    },
                    "deepseek-v4.1-flash": {
                      "limit": {"context": 1024000, "output": 393216},
                      "modalities": {"input": ["text", "image"], "reasoning_effort": ["low", "high", "max"]}
                    }
                  }
                }
                """;

        ModelConfigDtos.Create request = json.readValue(payload, ModelConfigDtos.Create.class);

        assertThat(request.models()).containsOnlyKeys("glm-5.3", "deepseek-v4.1-flash");
        assertThat(request.models().get("deepseek-v4.1-flash").modalities().input())
                .containsExactly("text", "image");
        assertThat(json.readTree(json.writeValueAsString(request.models())))
                .isEqualTo(json.readTree(payload).get("models"));
    }
}

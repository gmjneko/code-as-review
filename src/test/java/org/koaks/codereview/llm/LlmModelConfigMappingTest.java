package org.koaks.codereview.llm;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.llm.domain.LlmModelConfig;

import static org.assertj.core.api.Assertions.assertThat;

class LlmModelConfigMappingTest {

    @Test
    void entityExposesNoArgsConstructorForMyBatis() throws NoSuchMethodException {
        assertThat(LlmModelConfig.class.getDeclaredConstructor()).isNotNull();
    }
}

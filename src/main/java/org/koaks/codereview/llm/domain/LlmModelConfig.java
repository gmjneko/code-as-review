package org.koaks.codereview.llm.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@Builder
@TableName("llm_model_config")
public class LlmModelConfig extends BaseEntity {

    private Long userId;
    private String name;
    private String baseUrl;
    private String modelName;
    private String apiKeyCipher;
    private Boolean isDefault;

}

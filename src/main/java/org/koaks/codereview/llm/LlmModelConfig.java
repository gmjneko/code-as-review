package org.koaks.codereview.llm;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@TableName("llm_model_config")
public class LlmModelConfig extends BaseEntity {

    private Long userId;
    private String name;
    private String baseUrl;
    private String modelName;
    private String apiKeyCipher;
    private Boolean isDefault;
}

package org.koaks.codereview.llm.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@TableName("llm_model_config")
public class LlmModelConfig extends BaseEntity {

    private Long userId;
    private String name;
    private String baseUrl;
    @TableField("model_names")
    private String modelNamesJson;
    private String apiKeyCipher;
    private Boolean isDefault;

}

package org.koaks.codereview.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.llm.domain.LlmModelConfig;

@Mapper
public interface LlmModelConfigMapper extends BaseMapper<LlmModelConfig> {
}

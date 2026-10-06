package org.koaks.codereview.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;

@Mapper
public interface RepositoryTriggerRuleMapper extends BaseMapper<RepositoryTriggerRule> {
}

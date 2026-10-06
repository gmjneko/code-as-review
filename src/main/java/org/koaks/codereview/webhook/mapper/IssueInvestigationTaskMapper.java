package org.koaks.codereview.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;

@Mapper
public interface IssueInvestigationTaskMapper extends BaseMapper<IssueInvestigationTask> {
}

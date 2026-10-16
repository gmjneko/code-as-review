package org.koaks.codereview.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.rag.domain.RagTask;

@Mapper
public interface RagTaskMapper extends BaseMapper<RagTask> {}

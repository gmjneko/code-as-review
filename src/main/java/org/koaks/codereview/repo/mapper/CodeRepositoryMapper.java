package org.koaks.codereview.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.repo.domain.CodeRepository;

@Mapper
public interface CodeRepositoryMapper extends BaseMapper<CodeRepository> {
}

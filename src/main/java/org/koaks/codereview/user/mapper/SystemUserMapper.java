package org.koaks.codereview.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.user.domain.SystemUser;

@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
}

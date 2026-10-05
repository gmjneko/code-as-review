package org.koaks.codereview.scm.credential.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.scm.credential.domain.ScmCredential;

@Mapper
public interface ScmCredentialMapper extends BaseMapper<ScmCredential> {
}

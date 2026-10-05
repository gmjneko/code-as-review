package org.koaks.codereview.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.koaks.codereview.review.domain.ReviewComment;

@Mapper
public interface ReviewCommentMapper extends BaseMapper<ReviewComment> {
}

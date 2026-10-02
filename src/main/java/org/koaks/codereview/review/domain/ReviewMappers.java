package org.koaks.codereview.review.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

public final class ReviewMappers {

    private ReviewMappers() {
    }

    @Mapper
    public interface TaskMapper extends BaseMapper<ReviewTask> {
    }

    @Mapper
    public interface CommentMapper extends BaseMapper<ReviewComment> {
    }
}

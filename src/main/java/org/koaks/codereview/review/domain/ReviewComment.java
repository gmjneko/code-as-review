package org.koaks.codereview.review.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.common.persistence.BaseEntity;

@Getter
@Setter
@TableName("review_comment")
public class ReviewComment extends BaseEntity {

    private Long taskId;
    private String filePath;
    private Integer startLine;
    private Integer endLine;
    private String category;
    private String severity;
    private String content;
    private String existingCode;
    private String suggestionCode;
    private Integer round;
    private ReviewEnums.CommentStatus status;
    private String filterReason;
    private String externalCommentId;

}

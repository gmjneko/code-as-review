package org.koaks.codereview.review.comment;

import lombok.Getter;
import lombok.Setter;
import org.koaks.codereview.review.domain.ReviewEnums;

/** A finding reported through {@code code_comment}, before and after the filter pass. */
@Getter
@Setter
public class CandidateComment {

    private final String id;
    private final String path;
    private final String content;
    private final String existingCode;
    private final String suggestionCode;
    private final String category;
    private final String severity;
    private final int round;
    private ReviewEnums.CommentStatus status;
    private String filterReason;
    private Integer startLine;
    private Integer endLine;

    public CandidateComment(String id, String path, String content, String existingCode, String suggestionCode,
                            String category, String severity, int round) {
        this.id = id;
        this.path = path;
        this.content = content;
        this.existingCode = existingCode;
        this.suggestionCode = suggestionCode;
        this.category = category;
        this.severity = severity;
        this.round = round;
    }
}

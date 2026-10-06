package org.koaks.codereview.review.publish;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.domain.ReviewComment;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.mapper.ReviewCommentMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stores findings as each review round settles them, filtered ones included, so progress is
 * visible while the task runs and a filter decision can be audited later.
 */
@Component
@RequiredArgsConstructor
public class ReviewCommentStore {

    private final ReviewCommentMapper commentMapper;

    /** Each comment must be passed exactly once; rows are only ever inserted. */
    @Transactional
    public void save(long taskId, List<CandidateComment> comments) {
        for (CandidateComment c : comments) {
            ReviewComment row = new ReviewComment();
            row.setTaskId(taskId);
            row.setFilePath(c.getPath());
            row.setStartLine(c.getStartLine());
            row.setEndLine(c.getEndLine());
            row.setCategory(c.getCategory());
            row.setSeverity(c.getSeverity());
            row.setContent(c.getContent());
            row.setExistingCode(c.getExistingCode());
            row.setSuggestionCode(c.getSuggestionCode());
            row.setRound(c.getRound());
            // Findings of a round that failed before its fact-check are kept as confirmed.
            row.setStatus(c.getStatus() == null ? ReviewEnums.CommentStatus.CONFIRMED : c.getStatus());
            row.setFilterReason(c.getFilterReason());
            commentMapper.insert(row);
        }
    }

}

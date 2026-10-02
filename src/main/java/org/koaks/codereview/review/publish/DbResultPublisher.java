package org.koaks.codereview.review.publish;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.domain.ReviewComment;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewMappers;
import org.koaks.codereview.review.domain.ReviewTask;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Stores every finding, filtered ones included, so a filter decision can be audited later. */
@Order(0)
@Component
@RequiredArgsConstructor
public class DbResultPublisher implements ResultPublisher {

    private final ReviewMappers.CommentMapper commentMapper;

    @Override
    public boolean supports(ReviewTask task) {
        return true;
    }

    @Override
    @Transactional
    public void publish(ReviewTask task, List<CandidateComment> comments) {
        for (CandidateComment c : comments) {
            ReviewComment row = new ReviewComment();
            row.setTaskId(task.getId());
            row.setFilePath(c.getPath());
            row.setStartLine(c.getStartLine());
            row.setEndLine(c.getEndLine());
            row.setCategory(c.getCategory());
            row.setSeverity(c.getSeverity());
            row.setContent(c.getContent());
            row.setExistingCode(c.getExistingCode());
            row.setSuggestionCode(c.getSuggestionCode());
            row.setRound(c.getRound());
            row.setStatus(c.getStatus() == null ? ReviewEnums.CommentStatus.CONFIRMED : c.getStatus());
            row.setFilterReason(c.getFilterReason());
            commentMapper.insert(row);
        }
    }
}

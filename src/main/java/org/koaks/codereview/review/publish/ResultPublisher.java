package org.koaks.codereview.review.publish;

import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.domain.ReviewTask;

import java.util.List;

/**
 * Delivers the findings of a finished review outside the console. Findings are already stored by
 * {@link ReviewCommentStore} while the review runs; GitHub/GitLab publishers will post them as
 * pull request review comments for tasks triggered from a pull request and record the remote ids.
 */
public interface ResultPublisher {

    boolean supports(ReviewTask task);

    void publish(ReviewTask task, List<CandidateComment> comments);

}

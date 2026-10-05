package org.koaks.codereview.review.task;

import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.git.GitCli;
import org.springframework.util.StringUtils;

public final class ReviewTargets {

    private ReviewTargets() {
    }

    public static ReviewTarget of(ReviewTask task) {
        return switch (task.getTargetType()) {
            case LOCAL_WORKING_TREE -> new ReviewTarget.LocalWorkingTree();
            case COMMIT_RANGE -> {
                if (!StringUtils.hasText(task.getBaseRef()) || !StringUtils.hasText(task.getHeadRef())) {
                    throw BizException.badRequest("COMMIT_RANGE requires baseRef and headRef");
                }
                try {
                    yield new ReviewTarget.CommitRange(GitCli.requireSafeRef(task.getBaseRef()),
                            GitCli.requireSafeRef(task.getHeadRef()));
                } catch (IllegalArgumentException e) {
                    throw BizException.badRequest(e.getMessage());
                }
            }
            case PULL_REQUEST -> new ReviewTarget.PullRequest(requireExternalRef(task));
            case ISSUE -> new ReviewTarget.Issue(requireExternalRef(task));
        };
    }

    private static String requireExternalRef(ReviewTask task) {
        if (!StringUtils.hasText(task.getExternalRef())) {
            throw BizException.badRequest(task.getTargetType() + " requires externalRef");
        }
        return task.getExternalRef();
    }
}

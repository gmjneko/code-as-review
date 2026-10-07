package org.koaks.codereview.common.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.review.domain.ReviewEnums.TaskStatus;
import org.koaks.codereview.review.mapper.ReviewTaskMapper;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.WebhookEnums.IssueTaskStatus;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveTaskCounter {

    private final ReviewTaskMapper reviewTasks;
    private final IssueInvestigationTaskMapper issueTasks;

    public long count(long userId) {
        long reviews = reviewTasks.selectCount(Wrappers.<org.koaks.codereview.review.domain.ReviewTask>lambdaQuery()
                .eq(org.koaks.codereview.review.domain.ReviewTask::getUserId, userId)
                .in(org.koaks.codereview.review.domain.ReviewTask::getStatus, TaskStatus.PENDING, TaskStatus.RUNNING));
        long issues = issueTasks.selectCount(Wrappers.<IssueInvestigationTask>lambdaQuery()
                .eq(IssueInvestigationTask::getUserId, userId)
                .in(IssueInvestigationTask::getStatus, IssueTaskStatus.PENDING, IssueTaskStatus.RUNNING));
        return reviews + issues;
    }
}

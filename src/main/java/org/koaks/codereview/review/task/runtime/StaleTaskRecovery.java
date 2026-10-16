package org.koaks.codereview.review.task.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.review.domain.ReviewEnums.TaskStatus;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.mapper.ReviewTaskMapper;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.WebhookEnums;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.koaks.codereview.webhook.service.IssueTaskRunner;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagTask;
import org.koaks.codereview.rag.mapper.RagTaskMapper;
import org.koaks.codereview.rag.service.RagTaskRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The executor queue lives in memory, so on startup RUNNING tasks are known to be dead and are
 * failed, while PENDING ones are queued again. Assumes a single service instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleTaskRecovery {

    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskRunner runner;
    private final IssueInvestigationTaskMapper issueTaskMapper;
    private final IssueTaskRunner issueRunner;
    private final RagTaskMapper ragTaskMapper;
    private final RagTaskRunner ragRunner;

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        // 线程池不持久化，服务重启后 RUNNING 任务已经没有执行者；先标记失败，再重新投递 PENDING。
        int failed = taskMapper.update(Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getStatus, TaskStatus.RUNNING)
                .set(ReviewTask::getStatus, TaskStatus.FAILED)
                .set(ReviewTask::getErrorMessage, "interrupted by service restart")
                .set(ReviewTask::getFinishedAt, Instant.now()));
        var pending = taskMapper.selectList(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getStatus, TaskStatus.PENDING)
                .orderByAsc(ReviewTask::getId));
        pending.forEach(t -> runner.submit(t.getId()));
        int issueFailed = issueTaskMapper.update(Wrappers.<IssueInvestigationTask>lambdaUpdate()
                .eq(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.RUNNING)
                .set(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.FAILED)
                .set(IssueInvestigationTask::getErrorMessage, "interrupted by service restart")
                .set(IssueInvestigationTask::getFinishedAt, Instant.now()));
        var pendingIssues = issueTaskMapper.selectList(Wrappers.<IssueInvestigationTask>lambdaQuery()
                .eq(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.PENDING)
                .orderByAsc(IssueInvestigationTask::getId));
        pendingIssues.forEach(t -> issueRunner.submit(t.getId()));
        int ragFailed = ragTaskMapper.update(Wrappers.<RagTask>lambdaUpdate()
                .eq(RagTask::getStatus, RagEnums.TaskStatus.RUNNING)
                .set(RagTask::getStatus, RagEnums.TaskStatus.FAILED)
                .set(RagTask::getErrorMessage, "interrupted by service restart")
                .set(RagTask::getFinishedAt, Instant.now()));
        var pendingRag = ragTaskMapper.selectList(Wrappers.<RagTask>lambdaQuery()
                .eq(RagTask::getStatus, RagEnums.TaskStatus.PENDING).orderByAsc(RagTask::getId));
        pendingRag.forEach(t -> ragRunner.submit(t.getId()));
        if (failed > 0 || !pending.isEmpty() || issueFailed > 0 || !pendingIssues.isEmpty() || ragFailed > 0 || !pendingRag.isEmpty()) {
            log.info("Recovered tasks: {} PR interrupted, {} PR re-queued, {} Issue interrupted, {} Issue re-queued, {} RAG interrupted, {} RAG re-queued",
                    failed, pending.size(), issueFailed, pendingIssues.size(), ragFailed, pendingRag.size());
        }
    }

}

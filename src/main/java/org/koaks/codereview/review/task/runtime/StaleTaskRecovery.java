package org.koaks.codereview.review.task.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.review.domain.ReviewEnums.TaskStatus;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.mapper.ReviewTaskMapper;
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

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int failed = taskMapper.update(Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getStatus, TaskStatus.RUNNING)
                .set(ReviewTask::getStatus, TaskStatus.FAILED)
                .set(ReviewTask::getErrorMessage, "interrupted by service restart")
                .set(ReviewTask::getFinishedAt, Instant.now()));
        var pending = taskMapper.selectList(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getStatus, TaskStatus.PENDING)
                .orderByAsc(ReviewTask::getId));
        pending.forEach(t -> runner.submit(t.getId()));
        if (failed > 0 || !pending.isEmpty()) {
            log.info("Recovered review tasks: {} interrupted, {} re-queued", failed, pending.size());
        }
    }
}

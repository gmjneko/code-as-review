package org.koaks.codereview.review.task.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.koaks.codereview.common.api.PageResult;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.llm.service.LlmModelConfigService;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.domain.ReviewComment;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewEnums.TaskStatus;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.mapper.ReviewCommentMapper;
import org.koaks.codereview.review.mapper.ReviewTaskMapper;
import org.koaks.codereview.review.task.dto.ReviewDtos;
import org.koaks.codereview.review.task.ReviewTargets;
import org.koaks.codereview.review.task.runtime.ReviewTaskRunner;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.ScmProvider;
import org.koaks.codereview.scm.ScmProviderRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ReviewTaskService {

    /** Bounds how much LLM spend one user can have queued at a time. */
    static final int MAX_ACTIVE_TASKS_PER_USER = 3;

    private final ReviewTaskMapper taskMapper;
    private final ReviewCommentMapper commentMapper;
    private final RepoService repoService;
    private final ScmProviderRegistry providers;
    private final LlmModelConfigService modelConfigs;
    private final ReviewTaskRunner runner;
    private final ReviewEnums.Effort defaultEffort;

    public ReviewTaskService(ReviewTaskMapper taskMapper, ReviewCommentMapper commentMapper,
                             RepoService repoService, ScmProviderRegistry providers,
                             LlmModelConfigService modelConfigs, ReviewTaskRunner runner,
                             CodeReviewProperties properties) {
        this.taskMapper = taskMapper;
        this.commentMapper = commentMapper;
        this.repoService = repoService;
        this.providers = providers;
        this.modelConfigs = modelConfigs;
        this.runner = runner;
        this.defaultEffort = ReviewEnums.Effort.valueOf(properties.review().defaultEffort().toUpperCase(Locale.ROOT));
    }

    public ReviewTask create(long userId, ReviewDtos.Create request) {
        CodeRepository repo = repoService.getOwned(userId, request.repositoryId());
        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setRepositoryId(repo.getId());
        task.setTargetType(request.targetType());
        task.setTriggerType(ReviewEnums.TriggerType.API);
        task.setBaseRef(request.baseRef());
        task.setHeadRef(request.headRef());
        task.setExternalRef(request.externalRef());
        task.setEffort(request.effort() == null ? defaultEffort : request.effort());
        task.setBackground(request.background());
        task.setModelConfigId(request.modelConfigId());
        task.setStatus(TaskStatus.PENDING);

        ScmProvider provider = providers.get(repo.getSourceType());
        ReviewTarget target = ReviewTargets.of(task);
        if (!provider.supports(target)) {
            throw BizException.badRequest(repo.getSourceType() + " repositories do not support " + request.targetType());
        }
        modelConfigs.checkUsable(userId, request.modelConfigId());
        Long active = taskMapper.selectCount(Wrappers.<ReviewTask>lambdaQuery()
                .eq(ReviewTask::getUserId, userId)
                .in(ReviewTask::getStatus, TaskStatus.PENDING, TaskStatus.RUNNING));
        if (active >= MAX_ACTIVE_TASKS_PER_USER) {
            throw BizException.conflict("too many active reviews; wait for one to finish");
        }

        taskMapper.insert(task);
        runner.submit(task.getId());
        return task;
    }

    public PageResult<ReviewDtos.TaskView> list(long userId, Long repositoryId, long page, long size) {
        Page<ReviewTask> result = taskMapper.selectPage(Page.of(Math.max(page, 1), Math.clamp(size, 1, 100)),
                Wrappers.<ReviewTask>lambdaQuery()
                        .eq(ReviewTask::getUserId, userId)
                        .eq(repositoryId != null, ReviewTask::getRepositoryId, repositoryId)
                        .orderByDesc(ReviewTask::getId));
        return PageResult.of(result, ReviewDtos.TaskView::of);
    }

    public ReviewTask getOwned(long userId, long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || task.getUserId() != userId) {
            throw BizException.notFound("review task");
        }
        return task;
    }

    public List<ReviewComment> comments(long userId, long taskId, boolean includeFiltered) {
        getOwned(userId, taskId);
        return commentMapper.selectList(Wrappers.<ReviewComment>lambdaQuery()
                .eq(ReviewComment::getTaskId, taskId)
                .eq(!includeFiltered, ReviewComment::getStatus, ReviewEnums.CommentStatus.CONFIRMED)
                .orderByAsc(ReviewComment::getFilePath)
                .orderByAsc(ReviewComment::getStartLine));
    }

    public void cancel(long userId, long taskId) {
        ReviewTask task = getOwned(userId, taskId);
        if (task.getStatus().terminal()) {
            throw BizException.conflict("review task already " + task.getStatus());
        }
        int dequeued = taskMapper.update(Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getStatus, TaskStatus.PENDING)
                .set(ReviewTask::getStatus, TaskStatus.CANCELLED)
                .set(ReviewTask::getFinishedAt, Instant.now()));
        if (dequeued == 0) {
            runner.cancelRunning(taskId);
        }
    }
}

package org.koaks.codereview.review.task.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.llm.factory.ChatModelFactory;
import org.koaks.codereview.llm.service.LlmModelConfigService;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.agent.CancellationToken;
import org.koaks.codereview.review.agent.ChangeReviewer;
import org.koaks.codereview.review.agent.TaskBudget;
import org.koaks.codereview.review.agent.TaskRuntime;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.diff.DiffParser;
import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.review.diff.FileSelector;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewEnums.TaskStatus;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.mapper.ReviewTaskMapper;
import org.koaks.codereview.review.publish.ResultPublisher;
import org.koaks.codereview.review.publish.ReviewCommentStore;
import org.koaks.codereview.review.task.ReviewTargets;
import org.koaks.codereview.scm.PreparedWorkspace;
import org.koaks.codereview.scm.ScmProviderRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Executes review tasks on the bounded review executor, one agent session per task. */
@Slf4j
@Component
public class ReviewTaskRunner {

    private final ReviewTaskMapper taskMapper;
    private final RepoService repoService;
    private final ScmProviderRegistry providers;
    private final LlmModelConfigService modelConfigs;
    private final ChatModelFactory modelFactory;
    private final ChangeReviewer reviewer;
    private final ReviewCommentStore commentStore;
    private final List<ResultPublisher> publishers;
    private final FileSelector fileSelector;
    private final CodeReviewProperties properties;
    private final TaskExecutor executor;
    private final Map<Long, CancellationToken> running = new ConcurrentHashMap<>();

    public ReviewTaskRunner(ReviewTaskMapper taskMapper, RepoService repoService,
                            ScmProviderRegistry providers, LlmModelConfigService modelConfigs,
                            ChatModelFactory modelFactory, ChangeReviewer reviewer, ReviewCommentStore commentStore,
                            List<ResultPublisher> publishers,
                            FileSelector fileSelector, CodeReviewProperties properties,
                            @Qualifier(ReviewTaskConfig.EXECUTOR) TaskExecutor executor) {
        this.taskMapper = taskMapper;
        this.repoService = repoService;
        this.providers = providers;
        this.modelConfigs = modelConfigs;
        this.modelFactory = modelFactory;
        this.reviewer = reviewer;
        this.commentStore = commentStore;
        this.publishers = publishers;
        this.fileSelector = fileSelector;
        this.properties = properties;
        this.executor = executor;
    }

    public void submit(long taskId) {
        try {
            executor.execute(() -> run(taskId));
        } catch (TaskRejectedException e) {
            finish(taskId, TaskStatus.FAILED, "review queue is full, try again later", null);
        }
    }

    /** @return whether a running task was signalled */
    public boolean cancelRunning(long taskId) {
        CancellationToken token = running.get(taskId);
        if (token == null) {
            return false;
        }
        token.cancel();
        return true;
    }

    void run(long taskId) {
        int claimed = taskMapper.update(Wrappers.<ReviewTask>lambdaUpdate()
                .eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getStatus, TaskStatus.PENDING)
                .set(ReviewTask::getStatus, TaskStatus.RUNNING)
                .set(ReviewTask::getStartedAt, Instant.now()));
        if (claimed == 0) {
            return;
        }
        ReviewTask task = taskMapper.selectById(taskId);
        CancellationToken token = new CancellationToken();
        running.put(taskId, token);
        TaskBudget budget = new TaskBudget(properties.review().maxTaskTokens());
        Path scratch = properties.workspaceRoot().resolve("tasks").resolve(Long.toString(taskId));
        try {
            Files.createDirectories(scratch);
            execute(task, token, budget, scratch);
        } catch (CancellationToken.CancelledException e) {
            finish(taskId, TaskStatus.CANCELLED, null, budget);
        } catch (BizException e) {
            finish(taskId, TaskStatus.FAILED, e.getMessage(), budget);
        } catch (Exception e) {
            log.error("Review task {} failed", taskId, e);
            finish(taskId, TaskStatus.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage(), budget);
        } finally {
            running.remove(taskId);
            deleteQuietly(scratch);
        }
    }

    private void execute(ReviewTask task, CancellationToken token, TaskBudget budget, Path scratch) {
        CodeRepository repo = repoService.getOwned(task.getUserId(), task.getRepositoryId());
        try (PreparedWorkspace ws = providers.get(repo.getSourceType()).prepare(repo, ReviewTargets.of(task), scratch)) {
            token.throwIfCancelled();
            List<FileDiff> diffs = DiffParser.parse(ws.diff());
            FileSelector.Selection selection = fileSelector.select(diffs);
            ReviewTask progress = new ReviewTask();
            progress.setId(task.getId());
            progress.setBaseSha(ws.baseSha());
            progress.setHeadSha(ws.headSha());
            progress.setFilesChanged(diffs.size());
            progress.setFilesReviewed(selection.reviewable().size());
            taskMapper.updateById(progress);
            log.info("Task {}: {} file(s) changed, reviewing {}", task.getId(), diffs.size(),
                    selection.reviewable().size());

            if (selection.reviewable().isEmpty()) {
                completeWithSummary(task.getId(), "No reviewable files in this change.", budget);
                return;
            }

            Model model = modelFactory.create(modelConfigs.resolve(task.getUserId(), task.getModelConfigId(), task.getModelName()));
            Map<String, FileDiff> byPath = new LinkedHashMap<>();
            diffs.stream().filter(d -> !d.binary()).forEach(d -> byPath.put(d.path(), d));
            TaskRuntime rt = new TaskRuntime(task.getId(), task.getUserId(), model, budget, token, byPath,
                    selection.context(), task.getBackground(), task.getEffort(), ws.codeRoot(), scratch,
                    repo.getExecutionMode() == null ? org.koaks.codereview.repo.domain.ExecutionMode.LOCAL
                            : repo.getExecutionMode());

            AtomicInteger confirmed = new AtomicInteger();
            ChangeReviewer.Outcome outcome = reviewer.review(rt, selection.reviewable(),
                    (comments, rounds) -> recordFindings(task.getId(), comments, rounds, confirmed, budget));
            token.throwIfCancelled();
            String publishWarning = null;
            for (ResultPublisher publisher : publishers) {
                try {
                    if (publisher.supports(task)) {
                        publisher.publish(task, outcome.comments());
                    }
                } catch (RuntimeException e) {
                    publishWarning = "result publishing failed: " + rootMessage(e);
                    log.warn("Task {} could not publish review results", task.getId(), e);
                }
            }

            ReviewTask done = new ReviewTask();
            done.setId(task.getId());
            done.setRoundsCompleted(outcome.roundsCompleted());
            done.setPlanResult(outcome.plan());
            done.setSummary(outcome.summary());
            done.setCommentCount((int) countConfirmed(outcome.comments()));
            taskMapper.updateById(done);
            String message = outcome.failure() != null ? outcome.failure()
                    : publishWarning != null ? publishWarning : outcome.warning();
            finish(task.getId(), outcome.failure() != null ? TaskStatus.FAILED : TaskStatus.SUCCEEDED, message, budget);
        }
    }

    /** Stores a settled batch of findings and the progress counters, so the console sees them live. */
    private void recordFindings(long taskId, List<CandidateComment> comments, int rounds, AtomicInteger confirmed,
                                TaskBudget budget) {
        commentStore.save(taskId, comments);
        ReviewTask progress = new ReviewTask();
        progress.setId(taskId);
        progress.setRoundsCompleted(rounds);
        progress.setCommentCount(confirmed.addAndGet((int) countConfirmed(comments)));
        progress.setInputTokens(budget.inputTokens());
        progress.setOutputTokens(budget.outputTokens());
        taskMapper.updateById(progress);
    }

    private void completeWithSummary(long taskId, String summary, TaskBudget budget) {
        ReviewTask done = new ReviewTask();
        done.setId(taskId);
        done.setSummary(summary);
        taskMapper.updateById(done);
        finish(taskId, TaskStatus.SUCCEEDED, null, budget);
    }

    private void finish(long taskId, TaskStatus status, String errorMessage, TaskBudget budget) {
        ReviewTask done = new ReviewTask();
        done.setId(taskId);
        done.setStatus(status);
        done.setErrorMessage(errorMessage);
        done.setFinishedAt(Instant.now());
        if (budget != null) {
            done.setInputTokens(budget.inputTokens());
            done.setOutputTokens(budget.outputTokens());
        }
        taskMapper.updateById(done);
    }

    static long countConfirmed(List<CandidateComment> comments) {
        return comments.stream().filter(c -> c.getStatus() != ReviewEnums.CommentStatus.FILTERED).count();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean scratch directory {}: {}", dir, e.getMessage());
        }
    }

}

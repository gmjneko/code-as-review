package org.koaks.codereview.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagTask;
import org.koaks.codereview.rag.mapper.RagTaskMapper;
import org.koaks.codereview.scm.github.GitHubClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
/**
 * RAG 任务执行器。
 *
 * <p>通过条件更新把 PENDING 原子地抢占为 RUNNING，避免多实例或恢复逻辑重复执行同一任务。
 * Milvus/GitHub 的异常会让任务失败，下一次由任务重试机制重新处理。</p>
 */
public class RagTaskRunner {
    private final RagTaskMapper tasks;
    private final RepoService repos;
    private final GitHubClient github;
    private final RagAssociationService associations;
    private final TaskExecutor executor;

    public RagTaskRunner(RagTaskMapper tasks, RepoService repos, GitHubClient github, RagAssociationService associations,
                         @Qualifier("ragTaskExecutor") TaskExecutor executor) {
        this.tasks = tasks; this.repos = repos; this.github = github; this.associations = associations; this.executor = executor;
    }

    public void submit(long taskId) {
        try { executor.execute(() -> run(taskId)); }
        catch (TaskRejectedException e) { fail(taskId, e.getMessage()); }
    }

    void run(long taskId) {
        // 条件更新承担轻量级分布式锁的作用；抢不到任务说明已被其他 worker 执行。
        int claimed = tasks.update(Wrappers.<RagTask>lambdaUpdate().eq(RagTask::getId, taskId)
                .eq(RagTask::getStatus, RagEnums.TaskStatus.PENDING)
                .set(RagTask::getStatus, RagEnums.TaskStatus.RUNNING).set(RagTask::getStartedAt, Instant.now()));
        if (claimed == 0) return;
        RagTask task = tasks.selectById(taskId);
        try {
            CodeRepository repo = repos.getOwned(task.getUserId(), task.getRepositoryId());
            RagTaskService.requireGithub(repo);
            if (task.getTaskType() == RagEnums.TaskType.ITEM_SYNC) {
                associations.process(repo, task.getItemType(), task.getItemNumber());
                finish(task, 1);
            } else if (task.getTaskType() == RagEnums.TaskType.ITEM_DELETE) {
                associations.remove(repo, task.getItemType(), task.getItemNumber());
                finish(task, 1);
            } else {
                List<Work> work = fullWork(repo);
                // 全量同步先拿到稳定的 Issue/PR 编号列表，再逐项走同一套增量处理逻辑。
                task.setItemsTotal(work.size()); tasks.updateById(task);
                int done = 0;
                for (Work item : work) {
                    associations.process(repo, item.type(), item.number());
                    task.setItemsDone(++done); tasks.updateById(task);
                }
                finish(task, done);
            }
        } catch (Exception e) {
            log.error("RAG task {} failed", taskId, e);
            fail(taskId, root(e));
        }
    }

    private List<Work> fullWork(CodeRepository repo) {
        List<Work> result = new ArrayList<>();
        github.issues(repo.getExternalFullName(), "all", repo.getCredentialId(), repo.getUserId()).stream()
                .filter(i -> i.pullRequest() == null).forEach(i -> result.add(new Work(RagEnums.ItemType.ISSUE, Integer.toString(i.number()))));
        github.pullRequests(repo.getExternalFullName(), "all", repo.getCredentialId(), repo.getUserId()).forEach(p -> result.add(new Work(RagEnums.ItemType.PR, Integer.toString(p.number()))));
        return result;
    }

    private void finish(RagTask task, int done) {
        task.setStatus(RagEnums.TaskStatus.SUCCEEDED); task.setItemsDone(done); task.setFinishedAt(Instant.now()); tasks.updateById(task);
    }
    private void fail(long taskId, String message) { RagTask t = tasks.selectById(taskId); if (t != null) { t.setStatus(RagEnums.TaskStatus.FAILED); t.setErrorMessage(message); t.setFinishedAt(Instant.now()); tasks.updateById(t); } }
    private static String root(Throwable e) { while (e.getCause() != null && e.getCause() != e) e = e.getCause(); return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private record Work(RagEnums.ItemType type, String number) {}
}

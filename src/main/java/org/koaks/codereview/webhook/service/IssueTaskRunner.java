package org.koaks.codereview.webhook.service;

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
import org.koaks.codereview.review.agent.IssueInvestigationReport;
import org.koaks.codereview.review.agent.IssueInvestigator;
import org.koaks.codereview.review.agent.TaskBudget;
import org.koaks.codereview.scm.PreparedWorkspace;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.ScmProviderRegistry;
import org.koaks.codereview.scm.github.GitHubClient;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.WebhookEnums;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class IssueTaskRunner {

    private final IssueInvestigationTaskMapper mapper;
    private final RepoService repos;
    private final ScmProviderRegistry providers;
    private final LlmModelConfigService modelConfigs;
    private final ChatModelFactory modelFactory;
    private final IssueInvestigator investigator;
    private final IssueResultPublisher publisher;
    private final CodeReviewProperties properties;
    private final GitHubClient github;
    private final TaskExecutor executor;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<Long, CancellationToken> running = new ConcurrentHashMap<>();

    public IssueTaskRunner(IssueInvestigationTaskMapper mapper, RepoService repos, ScmProviderRegistry providers,
                           LlmModelConfigService modelConfigs, ChatModelFactory modelFactory,
                           IssueInvestigator investigator, IssueResultPublisher publisher,
                           CodeReviewProperties properties, GitHubClient github,
                           @Qualifier(org.koaks.codereview.review.task.runtime.ReviewTaskConfig.EXECUTOR)
                           TaskExecutor executor) {
        this.mapper = mapper;
        this.repos = repos;
        this.providers = providers;
        this.modelConfigs = modelConfigs;
        this.modelFactory = modelFactory;
        this.investigator = investigator;
        this.publisher = publisher;
        this.properties = properties;
        this.github = github;
        this.executor = executor;
    }

    public void submit(long taskId) {
        try {
            executor.execute(() -> run(taskId));
        } catch (TaskRejectedException e) {
            IssueInvestigationTask task = mapper.selectById(taskId);
            if (task == null) {
                finish(taskId, WebhookEnums.IssueTaskStatus.FAILED, e.getMessage(), null, null, null, null);
            } else {
                failAndPublish(task, e.getMessage() == null ? "issue task executor rejected the task" : e.getMessage(), null);
            }
        }
    }

    public boolean cancelRunning(long taskId) {
        CancellationToken token = running.get(taskId);
        if (token == null) return false;
        token.cancel();
        return true;
    }

    void run(long taskId) {
        int claimed = mapper.update(Wrappers.<IssueInvestigationTask>lambdaUpdate()
                .eq(IssueInvestigationTask::getId, taskId)
                .eq(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.PENDING)
                .set(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.RUNNING)
                .set(IssueInvestigationTask::getStartedAt, Instant.now()));
        if (claimed == 0) return;
        IssueInvestigationTask task = mapper.selectById(taskId);
        if (task == null) return;
        CancellationToken token = new CancellationToken();
        running.put(taskId, token);
        TaskBudget budget = new TaskBudget(properties.review().maxTaskTokens());
        Path scratch = properties.workspaceRoot().resolve("issue-tasks").resolve(Long.toString(taskId));
        try {
            Files.createDirectories(scratch);
            execute(task, token, budget, scratch);
        } catch (CancellationToken.CancelledException e) {
            finish(taskId, WebhookEnums.IssueTaskStatus.CANCELLED, null, budget, null, null, null);
        } catch (BizException e) {
            failAndPublish(task, e.getMessage(), budget);
        } catch (Exception e) {
            log.error("Issue task {} failed", taskId, e);
            failAndPublish(task, rootMessage(e), budget);
        } finally {
            running.remove(taskId);
            deleteQuietly(scratch);
        }
    }

    private void execute(IssueInvestigationTask task, CancellationToken token, TaskBudget budget, Path scratch) {
        CodeRepository repo = repos.getOwned(task.getUserId(), task.getRepositoryId());
        GitHubIssueData issue = loadIssue(repo, task);
        String baseRef = repo.getDefaultBranch();
        if (baseRef == null || baseRef.isBlank()) {
            baseRef = github.repository(repo.getExternalFullName(), repo.getCredentialId(), repo.getUserId()).defaultBranch();
        }
        try (PreparedWorkspace ws = providers.get(repo.getSourceType()).prepare(repo,
                new ReviewTarget.Issue(task.getIssueNumber()), scratch)) {
            token.throwIfCancelled();
            task.setBaseRef(baseRef);
            task.setBaseSha(ws.baseSha());
            mapper.updateById(task);
            Model model = modelFactory.create(modelConfigs.resolve(task.getUserId(), task.getModelConfigId(), task.getModelName()));
            IssueInvestigator.IssueInput input = new IssueInvestigator.IssueInput(issue.title(), issue.body(),
                    issue.state(), issue.author(), issue.labels(), issue.comments(), task.getBackground(),
                    task.getBaseRef(), task.getBaseSha());
            IssueInvestigationReport report = investigator.investigate(task.getId(), task.getUserId(), model, budget,
                    token, ws.codeRoot(), scratch, task.getExecutionMode(), input);
            IssueResultPublisher.Published published = publisher.publish(task, repo, report, null);
            finish(task.getId(), WebhookEnums.IssueTaskStatus.SUCCEEDED, null, budget, report,
                    published.markdown(), published.commentId());
        }
    }

    private GitHubIssueData loadIssue(CodeRepository repo, IssueInvestigationTask task) {
        if (repo.getSourceType() != org.koaks.codereview.repo.domain.SourceType.GITHUB
                || repo.getExternalFullName() == null || repo.getCredentialId() == null) {
            throw BizException.badRequest("Issue investigations currently require GitHub");
        }
        GitHubClient.GitHubIssue issue = github.issue(repo.getExternalFullName(), task.getIssueNumber(),
                repo.getCredentialId(), repo.getUserId());
        if (issue == null) throw BizException.badRequest("GitHub Issue was not found");
        List<GitHubClient.GitHubIssueComment> comments = github.issueComments(repo.getExternalFullName(),
                task.getIssueNumber(), repo.getCredentialId(), repo.getUserId());
        StringBuilder commentText = new StringBuilder();
        int count = 0;
        for (GitHubClient.GitHubIssueComment comment : comments) {
            if (count++ >= 50) break;
            String body = comment.body() == null ? "" : comment.body().strip();
            if (body.isEmpty()) continue;
            String author = comment.user() == null ? "unknown" : String.valueOf(comment.user().login());
            String line = author + ": " + body;
            if (commentText.length() > 0) commentText.append("\n\n");
            int remaining = 30_000 - commentText.length();
            if (remaining <= 0) break;
            commentText.append(line, 0, Math.min(line.length(), remaining));
            if (line.length() > remaining) break;
        }
        String labels = issue.labels() == null ? "" : issue.labels().stream()
                .map(GitHubClient.Label::name).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));
        return new GitHubIssueData(issue.title(), issue.body(), issue.state(),
                issue.user() == null ? null : issue.user().login(), labels, commentText.toString());
    }

    private void failAndPublish(IssueInvestigationTask task, String message, TaskBudget budget) {
        try {
            CodeRepository repo = repos.getOwned(task.getUserId(), task.getRepositoryId());
            IssueResultPublisher.Published published = publisher.publish(task, repo, null, message);
            finish(task.getId(), WebhookEnums.IssueTaskStatus.FAILED, message, budget, null,
                    published.markdown(), published.commentId());
        } catch (RuntimeException publishError) {
            finish(task.getId(), WebhookEnums.IssueTaskStatus.FAILED,
                    message + "; result publishing failed: " + rootMessage(publishError), budget, null, null, null);
        }
    }

    private void finish(long taskId, WebhookEnums.IssueTaskStatus status, String error, TaskBudget budget,
                        IssueInvestigationReport report, String markdown, String commentId) {
        IssueInvestigationTask done = new IssueInvestigationTask();
        done.setId(taskId);
        done.setStatus(status);
        done.setErrorMessage(error);
        done.setFinishedAt(Instant.now());
        if (budget != null) {
            done.setInputTokens(budget.inputTokens());
            done.setOutputTokens(budget.outputTokens());
        }
        if (report != null) {
            done.setSummary(report.summary());
            done.setReportJson(json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
        done.setReportMarkdown(markdown);
        done.setExternalCommentId(commentId);
        mapper.updateById(done);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record GitHubIssueData(String title, String body, String state, String author,
                                   String labels, String comments) {
    }
}

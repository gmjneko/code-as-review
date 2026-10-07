package org.koaks.codereview.webhook.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.api.PageResult;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.common.persistence.ActiveTaskCounter;
import org.koaks.codereview.common.persistence.AfterCommit;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.llm.service.LlmModelConfigService;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.ExecutionMode;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.WebhookEnums;
import org.koaks.codereview.webhook.dto.IssueTaskDtos;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class IssueTaskService {

    private static final int MAX_ACTIVE_TASKS_PER_USER = 3;

    private final IssueInvestigationTaskMapper mapper;
    private final RepoService repos;
    private final LlmModelConfigService models;
    private final IssueTaskRunner runner;
    private final ActiveTaskCounter activeTasks;
    private final CodeReviewProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    @Transactional
    public IssueInvestigationTask create(long userId, IssueTaskDtos.Create request) {
        CodeRepository repo = repos.getOwned(userId, request.repositoryId());
        if (repo.getSourceType() != org.koaks.codereview.repo.domain.SourceType.GITHUB) {
            throw BizException.badRequest("Issue investigations currently require a GitHub repository");
        }
        models.checkUsable(userId, request.modelConfigId(), request.modelName());
        String selectedModel = models.resolve(userId, request.modelConfigId(), request.modelName()).modelName();
        checkQuota(userId);
        IssueInvestigationTask task = newTask(repo, request.issueNumber(), null, null,
                ReviewEnums.TriggerType.API, request.effort(), request.modelConfigId(), selectedModel,
                request.background());
        mapper.insert(task);
        AfterCommit.run(() -> runner.submit(task.getId()));
        return task;
    }

    @Transactional
    public IssueInvestigationTask createFromWebhook(CodeRepository repo, String issueNumber, Long webhookEventId,
                                                    String command, ReviewEnums.TriggerType triggerType,
                                                    ReviewEnums.Effort effort, Long modelConfigId, String modelName,
                                                    String background) {
        if (webhookEventId != null) {
            IssueInvestigationTask existing = mapper.selectOne(Wrappers.<IssueInvestigationTask>lambdaQuery()
                    .eq(IssueInvestigationTask::getWebhookEventId, webhookEventId).last("LIMIT 1"));
            if (existing != null) return existing;
        }
        models.checkUsable(repo.getUserId(), modelConfigId, modelName);
        String selectedModel = models.resolve(repo.getUserId(), modelConfigId, modelName).modelName();
        checkQuota(repo.getUserId());
        IssueInvestigationTask task = newTask(repo, issueNumber, webhookEventId, command, triggerType, effort,
                modelConfigId, selectedModel, background);
        mapper.insert(task);
        AfterCommit.run(() -> runner.submit(task.getId()));
        return task;
    }

    private IssueInvestigationTask newTask(CodeRepository repo, String issueNumber, Long webhookEventId,
                                           String command, ReviewEnums.TriggerType triggerType,
                                           ReviewEnums.Effort effort,
                                           Long modelConfigId, String modelName, String background) {
        IssueInvestigationTask task = new IssueInvestigationTask();
        task.setRepositoryId(repo.getId());
        task.setUserId(repo.getUserId());
        task.setIssueNumber(issueNumber == null ? null : issueNumber.strip());
        task.setWebhookEventId(webhookEventId);
        task.setCommand(command);
        task.setTriggerType(triggerType);
        task.setExecutionMode(repo.getExecutionMode() == null ? ExecutionMode.LOCAL : repo.getExecutionMode());
        task.setBaseRef(StringUtils.hasText(repo.getDefaultBranch()) ? repo.getDefaultBranch() : null);
        task.setEffort(effort == null ? defaultEffort() : effort);
        task.setBackground(blankToNull(background));
        task.setModelConfigId(modelConfigId);
        task.setModelName(blankToNull(modelName));
        task.setStatus(WebhookEnums.IssueTaskStatus.PENDING);
        task.setInputTokens(0L);
        task.setOutputTokens(0L);
        return task;
    }

    private void checkQuota(long userId) {
        if (activeTasks.count(userId) >= MAX_ACTIVE_TASKS_PER_USER) {
            throw BizException.conflict("too many active reviews; wait for one to finish");
        }
    }

    private ReviewEnums.Effort defaultEffort() {
        return ReviewEnums.Effort.valueOf(properties.review().defaultEffort().toUpperCase());
    }

    public PageResult<IssueTaskDtos.TaskView> list(long userId, Long repositoryId, long page, long size) {
        Page<IssueInvestigationTask> result = mapper.selectPage(Page.of(Math.max(page, 1), Math.clamp(size, 1, 100)),
                Wrappers.<IssueInvestigationTask>lambdaQuery().eq(IssueInvestigationTask::getUserId, userId)
                        .eq(repositoryId != null, IssueInvestigationTask::getRepositoryId, repositoryId)
                        .orderByDesc(IssueInvestigationTask::getId));
        return PageResult.of(result, IssueTaskDtos.TaskView::of);
    }

    public IssueInvestigationTask getOwned(long userId, long id) {
        IssueInvestigationTask task = mapper.selectById(id);
        if (task == null || task.getUserId() != userId) throw BizException.notFound("issue task");
        return task;
    }

    public IssueTaskDtos.ReportView report(long userId, long id) {
        IssueInvestigationTask task = getOwned(userId, id);
        if (!StringUtils.hasText(task.getReportJson())) return null;
        try {
            org.koaks.codereview.review.agent.IssueInvestigationReport report =
                    json.readValue(task.getReportJson(), org.koaks.codereview.review.agent.IssueInvestigationReport.class);
            return IssueTaskDtos.ReportView.of(report, task.getReportMarkdown());
        } catch (RuntimeException e) {
            throw BizException.badRequest("stored issue report is invalid");
        }
    }

    public void cancel(long userId, long id) {
        IssueInvestigationTask task = getOwned(userId, id);
        if (task.getStatus().terminal()) throw BizException.conflict("issue task already " + task.getStatus());
        int changed = mapper.update(Wrappers.<IssueInvestigationTask>lambdaUpdate()
                .eq(IssueInvestigationTask::getId, id)
                .eq(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.PENDING)
                .set(IssueInvestigationTask::getStatus, WebhookEnums.IssueTaskStatus.CANCELLED)
                .set(IssueInvestigationTask::getFinishedAt, Instant.now()));
        if (changed == 0) runner.cancelRunning(id);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}

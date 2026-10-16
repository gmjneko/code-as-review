package org.koaks.codereview.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.common.persistence.AfterCommit;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.rag.domain.RagTask;
import org.koaks.codereview.rag.dto.RagDtos;
import org.koaks.codereview.rag.mapper.RagRelationMapper;
import org.koaks.codereview.rag.mapper.RagTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagTaskService {
    private final RagTaskMapper tasks;
    private final RagRelationMapper relations;
    private final RepoService repos;
    private final RagTaskRunner runner;
    private final CodeReviewProperties properties;

    public boolean enabled() { return properties.rag().enabled(); }

    @Transactional
    public RagTask fullSync(long userId, long repositoryId) {
        if (!enabled()) throw BizException.badRequest("RAG is disabled");
        CodeRepository repo = repos.getOwned(userId, repositoryId);
        requireGithub(repo);
        RagTask existing = tasks.selectOne(Wrappers.<RagTask>lambdaQuery().eq(RagTask::getRepositoryId, repositoryId)
                .eq(RagTask::getTaskType, RagEnums.TaskType.FULL_SYNC)
                .in(RagTask::getStatus, RagEnums.TaskStatus.PENDING, RagEnums.TaskStatus.RUNNING)
                .last("LIMIT 1"));
        if (existing != null) return existing;
        RagTask task = new RagTask(); task.setRepositoryId(repositoryId); task.setUserId(userId); task.setTaskType(RagEnums.TaskType.FULL_SYNC);
        task.setDedupeKey("full-sync:" + Instant.now().toEpochMilli()); task.setStatus(RagEnums.TaskStatus.PENDING);
        task.setItemsTotal(0); task.setItemsDone(0); task.setChunksDone(0); tasks.insert(task);
        AfterCommit.run(() -> runner.submit(task.getId()));
        return task;
    }

    @Transactional
    public RagTask enqueue(CodeRepository repo, RagEnums.ItemType type, String number, Long webhookEventId) {
        return enqueue(repo, type, number, webhookEventId, RagEnums.TaskType.ITEM_SYNC);
    }

    @Transactional
    public RagTask enqueueDeleted(CodeRepository repo, RagEnums.ItemType type, String number, Long webhookEventId) {
        return enqueue(repo, type, number, webhookEventId, RagEnums.TaskType.ITEM_DELETE);
    }

    private RagTask enqueue(CodeRepository repo, RagEnums.ItemType type, String number, Long webhookEventId, RagEnums.TaskType taskType) {
        if (!enabled()) return null;
        String key = "item:" + type + ":" + number;
        RagTask task = tasks.selectOne(Wrappers.<RagTask>lambdaQuery().eq(RagTask::getRepositoryId, repo.getId())
                .eq(RagTask::getDedupeKey, key).last("LIMIT 1"));
        if (task == null) {
            task = new RagTask(); task.setRepositoryId(repo.getId()); task.setUserId(repo.getUserId()); task.setTaskType(taskType);
            task.setItemType(type); task.setItemNumber(number); task.setWebhookEventId(webhookEventId); task.setDedupeKey(key);
            task.setStatus(RagEnums.TaskStatus.PENDING); task.setItemsTotal(1); task.setItemsDone(0); task.setChunksDone(0); tasks.insert(task);
            RagTask submitted = task;
            AfterCommit.run(() -> runner.submit(submitted.getId()));
            return task;
        }
        if (task.getStatus() != RagEnums.TaskStatus.RUNNING) {
            task.setTaskType(taskType); task.setWebhookEventId(webhookEventId); task.setStatus(RagEnums.TaskStatus.PENDING); task.setErrorMessage(null); task.setFinishedAt(null); tasks.updateById(task);
            RagTask submitted = task;
            AfterCommit.run(() -> runner.submit(submitted.getId()));
        }
        return task;
    }

    public RagTask getOwned(long userId, long repositoryId, long taskId) {
        RagTask task = tasks.selectById(taskId);
        if (task == null || task.getRepositoryId() != repositoryId || task.getUserId() != userId) throw BizException.notFound("RAG task");
        return task;
    }

    public List<RagRelation> relations(long userId, long repositoryId, RagEnums.ItemType type, String number) {
        repos.getOwned(userId, repositoryId);
        return relations.selectList(Wrappers.<RagRelation>lambdaQuery().eq(RagRelation::getRepositoryId, repositoryId)
                .and(w -> w.and(x -> x.eq(RagRelation::getSourceType, type).eq(RagRelation::getSourceNumber, number))
                        .or(x -> x.eq(RagRelation::getTargetType, type).eq(RagRelation::getTargetNumber, number)))
                .orderByDesc(RagRelation::getUpdatedAt));
    }

    static void requireGithub(CodeRepository repo) {
        if (repo.getSourceType() != SourceType.GITHUB) throw BizException.badRequest("RAG currently requires a GitHub repository");
    }
}

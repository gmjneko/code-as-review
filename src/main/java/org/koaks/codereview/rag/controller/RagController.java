package org.koaks.codereview.rag.controller;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.dto.RagDtos;
import org.koaks.codereview.rag.service.RagTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/rag")
@RequiredArgsConstructor
/**
 * RAG 管理接口。
 *
 * <p>控制器只负责鉴权范围内的参数接收和结果转换，真正的同步、任务去重以及关系查询都
 * 委托给 {@link RagTaskService}。这样 Web API 不会直接操作 Milvus、ES 或 GitHub，避免
 * 把外部服务细节暴露到接口层。</p>
 */
public class RagController {
    private final RagTaskService tasks;

    @PostMapping("/sync")
    public ApiResponse<RagDtos.TaskView> sync(@PathVariable long repositoryId) {
        // 全量同步是异步任务：接口立即返回任务记录，客户端通过 taskId 查询进度。
        return ApiResponse.ok(RagDtos.TaskView.of(tasks.fullSync(CurrentUser.id(), repositoryId)));
    }

    @GetMapping("/sync/{taskId}")
    public ApiResponse<RagDtos.TaskView> task(@PathVariable long repositoryId, @PathVariable long taskId) {
        // getOwned 会同时校验用户和仓库，防止用户通过猜测 taskId 读取其他仓库的任务。
        return ApiResponse.ok(RagDtos.TaskView.of(tasks.getOwned(CurrentUser.id(), repositoryId, taskId)));
    }

    @GetMapping("/relations")
    public ApiResponse<List<RagDtos.RelationView>> relations(@PathVariable long repositoryId,
                                                              @RequestParam RagEnums.ItemType itemType,
                                                              @RequestParam String number) {
        // 关系从数据库读取；评论写回属于异步/可重试副作用，不影响这个查询接口。
        return ApiResponse.ok(tasks.relations(CurrentUser.id(), repositoryId, itemType, number).stream()
                .map(RagDtos.RelationView::of).toList());
    }
}

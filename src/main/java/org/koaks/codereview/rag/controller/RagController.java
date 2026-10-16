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
public class RagController {
    private final RagTaskService tasks;

    @PostMapping("/sync")
    public ApiResponse<RagDtos.TaskView> sync(@PathVariable long repositoryId) {
        return ApiResponse.ok(RagDtos.TaskView.of(tasks.fullSync(CurrentUser.id(), repositoryId)));
    }

    @GetMapping("/sync/{taskId}")
    public ApiResponse<RagDtos.TaskView> task(@PathVariable long repositoryId, @PathVariable long taskId) {
        return ApiResponse.ok(RagDtos.TaskView.of(tasks.getOwned(CurrentUser.id(), repositoryId, taskId)));
    }

    @GetMapping("/relations")
    public ApiResponse<List<RagDtos.RelationView>> relations(@PathVariable long repositoryId,
                                                              @RequestParam RagEnums.ItemType itemType,
                                                              @RequestParam String number) {
        return ApiResponse.ok(tasks.relations(CurrentUser.id(), repositoryId, itemType, number).stream()
                .map(RagDtos.RelationView::of).toList());
    }
}

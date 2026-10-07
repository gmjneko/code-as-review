package org.koaks.codereview.webhook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.common.api.PageResult;
import org.koaks.codereview.webhook.dto.IssueTaskDtos;
import org.koaks.codereview.webhook.service.IssueTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueTaskService service;

    @PostMapping
    public ApiResponse<IssueTaskDtos.TaskView> create(@Valid @RequestBody IssueTaskDtos.Create request) {
        return ApiResponse.ok(IssueTaskDtos.TaskView.of(service.create(CurrentUser.id(), request)));
    }

    @GetMapping
    public ApiResponse<PageResult<IssueTaskDtos.TaskView>> list(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.list(CurrentUser.id(), repositoryId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueTaskDtos.TaskView> get(@PathVariable long id) {
        return ApiResponse.ok(IssueTaskDtos.TaskView.of(service.getOwned(CurrentUser.id(), id)));
    }

    @GetMapping("/{id}/report")
    public ApiResponse<IssueTaskDtos.ReportView> report(@PathVariable long id) {
        return ApiResponse.ok(service.report(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long id) {
        service.cancel(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}

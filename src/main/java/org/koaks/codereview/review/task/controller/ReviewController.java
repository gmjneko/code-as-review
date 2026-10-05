package org.koaks.codereview.review.task.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.common.api.PageResult;
import org.koaks.codereview.review.task.dto.ReviewDtos;
import org.koaks.codereview.review.task.service.ReviewTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewTaskService service;

    @PostMapping
    public ApiResponse<ReviewDtos.TaskView> create(@Valid @RequestBody ReviewDtos.Create request) {
        return ApiResponse.ok(ReviewDtos.TaskView.of(service.create(CurrentUser.id(), request)));
    }

    @GetMapping
    public ApiResponse<PageResult<ReviewDtos.TaskView>> list(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.list(CurrentUser.id(), repositoryId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewDtos.TaskView> get(@PathVariable long id) {
        return ApiResponse.ok(ReviewDtos.TaskView.of(service.getOwned(CurrentUser.id(), id)));
    }

    @GetMapping("/{id}/comments")
    public ApiResponse<List<ReviewDtos.CommentView>> comments(
            @PathVariable long id, @RequestParam(defaultValue = "false") boolean includeFiltered) {
        return ApiResponse.ok(service.comments(CurrentUser.id(), id, includeFiltered).stream()
                .map(ReviewDtos.CommentView::of).toList());
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long id) {
        service.cancel(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}

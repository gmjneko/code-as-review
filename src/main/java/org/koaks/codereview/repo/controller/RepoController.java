package org.koaks.codereview.repo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.repo.dto.RepoDtos;
import org.koaks.codereview.repo.service.RepoService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @PostMapping
    public ApiResponse<RepoDtos.View> create(@Valid @RequestBody RepoDtos.Create request) {
        return ApiResponse.ok(RepoDtos.View.of(repoService.create(CurrentUser.id(), request)));
    }

    @GetMapping
    public ApiResponse<List<RepoDtos.View>> list() {
        return ApiResponse.ok(repoService.list(CurrentUser.id()).stream().map(RepoDtos.View::of).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<RepoDtos.View> get(@PathVariable long id) {
        return ApiResponse.ok(RepoDtos.View.of(repoService.getOwned(CurrentUser.id(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<RepoDtos.View> update(@PathVariable long id, @Valid @RequestBody RepoDtos.Update request) {
        return ApiResponse.ok(RepoDtos.View.of(repoService.update(CurrentUser.id(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repoService.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}

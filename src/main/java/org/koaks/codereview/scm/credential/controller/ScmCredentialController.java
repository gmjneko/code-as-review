package org.koaks.codereview.scm.credential.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.scm.credential.dto.ScmCredentialDtos;
import org.koaks.codereview.scm.credential.service.ScmCredentialService;
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
@RequestMapping("/api/scm-credentials")
@RequiredArgsConstructor
public class ScmCredentialController {

    private final ScmCredentialService service;

    @PostMapping
    public ApiResponse<ScmCredentialDtos.View> create(@Valid @RequestBody ScmCredentialDtos.Create request) {
        return ApiResponse.ok(service.create(CurrentUser.id(), request));
    }

    @GetMapping
    public ApiResponse<List<ScmCredentialDtos.View>> list() {
        return ApiResponse.ok(service.list(CurrentUser.id()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScmCredentialDtos.View> update(@PathVariable long id,
                                                       @Valid @RequestBody ScmCredentialDtos.Update request) {
        return ApiResponse.ok(service.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(CurrentUser.id(), id);
        return ApiResponse.ok();
    }
}

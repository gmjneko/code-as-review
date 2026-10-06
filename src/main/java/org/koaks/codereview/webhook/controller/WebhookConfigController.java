package org.koaks.codereview.webhook.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.auth.security.CurrentUser;
import org.koaks.codereview.common.api.ApiResponse;
import org.koaks.codereview.webhook.dto.TriggerRuleDtos;
import org.koaks.codereview.webhook.dto.WebhookDtos;
import org.koaks.codereview.webhook.service.TriggerRuleService;
import org.koaks.codereview.webhook.service.WebhookConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
@RequiredArgsConstructor
public class WebhookConfigController {

    private final WebhookConfigService webhooks;
    private final TriggerRuleService rules;

    @PostMapping("/github-webhook")
    public ApiResponse<WebhookDtos.Config> rotate(@PathVariable long repositoryId,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.ok(webhooks.rotate(CurrentUser.id(), repositoryId, endpoint(request)));
    }

    @GetMapping("/github-webhook")
    public ApiResponse<WebhookDtos.Config> get(@PathVariable long repositoryId,
                                                jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.ok(webhooks.get(CurrentUser.id(), repositoryId, endpoint(request)));
    }

    @PutMapping("/github-trigger-rules")
    public ApiResponse<List<TriggerRuleDtos.View>> replace(@PathVariable long repositoryId,
                                                            @Valid @RequestBody TriggerRuleDtos.Replace request) {
        return ApiResponse.ok(rules.replace(CurrentUser.id(), repositoryId, request).stream()
                .map(TriggerRuleDtos.View::of).toList());
    }

    private static String endpoint(jakarta.servlet.http.HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName()
                + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort())
                + "/api/webhooks/github";
    }
}

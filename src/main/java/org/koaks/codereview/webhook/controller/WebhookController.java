package org.koaks.codereview.webhook.controller;

import org.koaks.codereview.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * Entry point reserved for GitHub/GitLab webhooks. The intended flow: verify the signature
 * ({@code X-Hub-Signature-256} / {@code X-Gitlab-Token}), de-duplicate on the delivery id
 * ({@code webhook_event}), parse a slash command at the start of an issue / PR comment (e.g.
 * {@code /review high}; mentions of a bot account are deliberately not supported), check that the
 * commenter may trigger reviews, persist a
 * {@code review_task} with trigger type {@code WEBHOOK_COMMAND}, and answer 202 before the review
 * runs asynchronously.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final org.koaks.codereview.webhook.service.GitHubWebhookService service;

    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<Void>> receive(@PathVariable String provider,
                                                       @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
                                                       @RequestHeader(value = "X-GitHub-Event", required = false) String event,
                                                       @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                                       @RequestBody(required = false) String payload) {
        if (!"github".equalsIgnoreCase(provider)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "unsupported webhook provider"));
        }
        service.receive(deliveryId, event, signature, payload);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok());
    }

}

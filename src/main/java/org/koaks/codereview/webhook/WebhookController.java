package org.koaks.codereview.webhook;

import org.koaks.codereview.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entry point reserved for GitHub/GitLab webhooks. The intended flow: verify the signature
 * ({@code X-Hub-Signature-256} / {@code X-Gitlab-Token}), de-duplicate on the delivery id
 * ({@code webhook_event}), check that the commenter may trigger reviews, persist a
 * {@code review_task} with trigger type {@code WEBHOOK_COMMAND}, and answer 202 before the review
 * runs asynchronously.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<Void>> receive(@PathVariable String provider) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error("NOT_IMPLEMENTED", "webhooks for " + provider + " are not supported yet"));
    }
}

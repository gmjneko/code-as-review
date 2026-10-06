package org.koaks.codereview.webhook.dto;

import java.util.List;

public final class WebhookDtos {

    private WebhookDtos() {
    }

    public record Config(boolean configured, String endpoint, String secret, List<TriggerRuleDtos.View> rules) {
    }
}

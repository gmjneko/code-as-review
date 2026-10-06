package org.koaks.codereview.webhook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;
import org.koaks.codereview.webhook.domain.WebhookEnums;

import java.util.List;

public final class TriggerRuleDtos {

    private TriggerRuleDtos() {
    }

    public record Rule(@NotNull WebhookEnums.EventKind eventKind,
                       @NotBlank @Size(max = 32) String action,
                       @NotNull WebhookEnums.Mode mode,
                       @Size(max = 64) String command,
                       Boolean enabled,
                       ReviewEnums.Effort effort,
                       Long modelConfigId) {
        public RepositoryTriggerRule toEntity(long repositoryId) {
            RepositoryTriggerRule rule = new RepositoryTriggerRule();
            rule.setRepositoryId(repositoryId);
            rule.setEventKind(eventKind);
            rule.setAction(action.strip().toLowerCase());
            rule.setMode(mode);
            rule.setCommand(command == null || command.isBlank() ? null : command.strip());
            rule.setEnabled(enabled == null || enabled);
            rule.setEffort(effort == null ? ReviewEnums.Effort.MEDIUM : effort);
            rule.setModelConfigId(modelConfigId);
            return rule;
        }
    }

    public record Replace(@NotNull @Valid @Size(max = 16) List<Rule> rules) {
    }

    public record View(Long id, WebhookEnums.EventKind eventKind, String action, WebhookEnums.Mode mode,
                       String command, Boolean enabled, ReviewEnums.Effort effort, Long modelConfigId) {
        public static View of(RepositoryTriggerRule rule) {
            return new View(rule.getId(), rule.getEventKind(), rule.getAction(), rule.getMode(), rule.getCommand(),
                    rule.getEnabled(), rule.getEffort(), rule.getModelConfigId());
        }
    }
}

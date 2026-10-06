package org.koaks.codereview.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.common.crypto.SecretCipher;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.task.service.ReviewTaskService;
import org.koaks.codereview.scm.github.GitHubClient;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;
import org.koaks.codereview.webhook.domain.WebhookEnums;
import org.koaks.codereview.webhook.domain.WebhookEvent;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.koaks.codereview.webhook.mapper.WebhookEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    private static final Pattern COMMAND = Pattern.compile("^/review(?:\\s+(low|medium|high))?(?:\\s*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_ASSOCIATIONS = Set.of("OWNER", "MEMBER", "COLLABORATOR");

    private final RepoService repos;
    private final SecretCipher cipher;
    private final WebhookEventMapper events;
    private final IssueInvestigationTaskMapper issueTasks;
    private final TriggerRuleService rules;
    private final ReviewTaskService reviewTasks;
    private final GitHubClient github;
    private final JsonMapper json;

    @Transactional
    public void receive(String deliveryId, String eventType, String signature, String rawPayload) {
        if (deliveryId == null || deliveryId.isBlank() || eventType == null || eventType.isBlank()) {
            return;
        }
        JsonNode payload;
        try {
            payload = json.readTree(rawPayload == null ? "{}" : rawPayload);
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed GitHub webhook {}", deliveryId);
            return;
        }
        String fullName = text(payload, "repository", "full_name");
        CodeRepository repo = fullName == null ? null : repos.findGithub(fullName);
        String secret = null;
        if (repo != null && repo.getWebhookSecretCipher() != null) {
            try {
                secret = cipher.decrypt(repo.getWebhookSecretCipher());
            } catch (RuntimeException e) {
                log.error("Cannot decrypt Webhook Secret for repository {}", repo.getId(), e);
            }
        }
        if (repo == null || secret == null || !validSignature(signature, rawPayload, secret)) {
            log.warn("Ignoring GitHub webhook with unknown repository or invalid signature: {}", deliveryId);
            return;
        }
        WebhookEvent event = new WebhookEvent();
        event.setProvider("GITHUB");
        event.setDeliveryId(deliveryId);
        event.setEventType(eventType);
        event.setRepositoryId(repo.getId());
        event.setPayload(rawPayload);
        event.setStatus(WebhookEnums.EventStatus.RECEIVED);
        try {
            events.insert(event);
        } catch (DuplicateKeyException e) {
            return;
        }
        try {
            dispatch(event, repo, payload);
            event.setStatus(WebhookEnums.EventStatus.DISPATCHED);
        } catch (IgnoredWebhook ignored) {
            event.setStatus(WebhookEnums.EventStatus.IGNORED);
        } catch (Exception e) {
            event.setStatus(WebhookEnums.EventStatus.FAILED);
            log.error("GitHub webhook {} failed", deliveryId, e);
        }
        events.updateById(event);
    }

    private void dispatch(WebhookEvent event, CodeRepository repo, JsonNode payload) {
        String eventType = event.getEventType().toLowerCase(Locale.ROOT);
        String action = text(payload, "action");
        if ("pull_request".equals(eventType)) {
            if (isBot(payload, "sender") || !("opened".equals(action) || "synchronize".equals(action))) {
                throw new IgnoredWebhook();
            }
            RepositoryTriggerRule rule = findRule(repo.getId(), WebhookEnums.EventKind.PULL_REQUEST, action,
                    WebhookEnums.Mode.AUTO, null);
            if (rule == null) throw new IgnoredWebhook();
            String number = text(payload, "number");
            String headSha = text(payload, "pull_request", "head", "sha");
            ReviewTask task = reviewTasks.createFromWebhook(repo, number,
                    text(payload, "pull_request", "base", "ref"),
                    text(payload, "pull_request", "head", "ref"), headSha,
                    ReviewEnums.TriggerType.AUTO_EVENT,
                    "github:pr:" + number + ":" + headSha,
                    rule.getEffort(), rule.getModelConfigId(), rule.getModelName(),
                    text(payload, "pull_request", "body"));
            event.setTaskId(task.getId());
            return;
        }
        if ("issues".equals(eventType)) {
            if (isBot(payload, "sender") || !"opened".equals(action)) throw new IgnoredWebhook();
            RepositoryTriggerRule rule = findRule(repo.getId(), WebhookEnums.EventKind.ISSUE, action,
                    WebhookEnums.Mode.AUTO, null);
            if (rule == null) throw new IgnoredWebhook();
            createIssueTask(event, repo, text(payload, "issue", "number"), null);
            return;
        }
        if ("issue_comment".equals(eventType)) {
            if (!"created".equals(action) || isBot(payload, "comment", "user")) throw new IgnoredWebhook();
            boolean isPr = text(payload, "issue", "pull_request", "url") != null;
            String body = text(payload, "comment", "body");
            Matcher command = body == null ? null : COMMAND.matcher(body.strip());
            if (command == null || !command.matches() || !authorized(payload)) throw new IgnoredWebhook();
            String commandName = "/review";
            WebhookEnums.EventKind kind = isPr ? WebhookEnums.EventKind.PR_COMMENT : WebhookEnums.EventKind.ISSUE_COMMENT;
            RepositoryTriggerRule rule = findRule(repo.getId(), kind, action, WebhookEnums.Mode.COMMAND, commandName);
            if (rule == null) throw new IgnoredWebhook();
            if (!isPr) {
                createIssueTask(event, repo, text(payload, "issue", "number"), commandName);
                return;
            }
            String number = text(payload, "issue", "number");
            GitHubClient.GitHubPullRequest pr = github.pullRequest(repo.getExternalFullName(), number,
                    repo.getCredentialId(), repo.getUserId());
            ReviewTask task = reviewTasks.createFromWebhook(repo, number, pr.base().ref(), pr.head().ref(), pr.head().sha(),
                    ReviewEnums.TriggerType.WEBHOOK_COMMAND,
                    "github:comment:" + text(payload, "comment", "id"), effort(command.group(1), rule.getEffort()),
                    rule.getModelConfigId(), rule.getModelName(), body);
            event.setTaskId(task.getId());
            return;
        }
        throw new IgnoredWebhook();
    }

    private void createIssueTask(WebhookEvent event, CodeRepository repo, String issueNumber, String command) {
        IssueInvestigationTask task = new IssueInvestigationTask();
        task.setRepositoryId(repo.getId());
        task.setUserId(repo.getUserId());
        task.setIssueNumber(issueNumber);
        task.setWebhookEventId(event.getId());
        task.setCommand(command);
        task.setStatus(WebhookEnums.IssueTaskStatus.PENDING);
        issueTasks.insert(task);
    }

    private RepositoryTriggerRule findRule(long repositoryId, WebhookEnums.EventKind kind, String action,
                                           WebhookEnums.Mode mode, String command) {
        RepositoryTriggerRule configured = rules.findEnabled(repositoryId, kind, action, mode, command);
        if (configured != null) return configured;
        if (rules.hasConfigured(repositoryId)) return null;
        return rules.defaults(repositoryId).stream()
                .filter(r -> r.getEventKind() == kind && r.getAction().equals(action) && r.getMode() == mode
                        && java.util.Objects.equals(r.getCommand(), command)).findFirst().orElse(null);
    }

    private static boolean validSignature(String signature, String payload, String secret) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean authorized(JsonNode payload) {
        String association = text(payload, "comment", "author_association");
        return association != null && ALLOWED_ASSOCIATIONS.contains(association.toUpperCase(Locale.ROOT));
    }

    private static ReviewEnums.Effort effort(String value, ReviewEnums.Effort fallback) {
        return value == null || value.isBlank() ? fallback : ReviewEnums.Effort.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static boolean isBot(JsonNode payload, String... path) {
        String[] fullPath = java.util.Arrays.copyOf(path, path.length + 1);
        fullPath[path.length] = "type";
        return "Bot".equalsIgnoreCase(text(payload, fullPath));
    }

    private static String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            if (current == null || current.get(part) == null || current.get(part).isNull()) return null;
            current = current.get(part);
        }
        return current == null ? null : current.asText();
    }

    private static final class IgnoredWebhook extends RuntimeException {
    }
}

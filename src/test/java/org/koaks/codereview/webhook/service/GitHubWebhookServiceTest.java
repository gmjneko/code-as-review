package org.koaks.codereview.webhook.service;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.common.crypto.SecretCipher;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.task.service.ReviewTaskService;
import org.koaks.codereview.scm.github.GitHubClient;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.koaks.codereview.webhook.domain.RepositoryTriggerRule;
import org.koaks.codereview.webhook.domain.WebhookEvent;
import org.koaks.codereview.webhook.mapper.IssueInvestigationTaskMapper;
import org.koaks.codereview.webhook.mapper.WebhookEventMapper;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubWebhookServiceTest {

    @Test
    void openedIssueIsRecordedWithoutStartingReviewRunner() throws Exception {
        RepoService repos = mock(RepoService.class);
        SecretCipher cipher = mock(SecretCipher.class);
        when(cipher.decrypt("ciphertext")).thenReturn("secret");
        WebhookEventMapper events = mock(WebhookEventMapper.class);
        IssueInvestigationTaskMapper issueTasks = mock(IssueInvestigationTaskMapper.class);
        TriggerRuleService rules = mock(TriggerRuleService.class);
        ReviewTaskService reviewTasks = mock(ReviewTaskService.class);
        GitHubClient github = mock(GitHubClient.class);
        GitHubWebhookService service = new GitHubWebhookService(repos, cipher, events, issueTasks, rules, reviewTasks,
                github, JsonMapper.builder().build());
        CodeRepository repo = new CodeRepository();
        repo.setId(7L);
        repo.setUserId(11L);
        repo.setSourceType(SourceType.GITHUB);
        repo.setExternalFullName("acme/demo");
        repo.setWebhookSecretCipher("ciphertext");
        when(repos.findGithub("acme/demo")).thenReturn(repo);
        when(rules.findEnabled(eq(7L), any(), eq("opened"), any(), isNull())).thenReturn(rule());
        when(events.insert(any(WebhookEvent.class))).thenAnswer(invocation -> {
            invocation.<WebhookEvent>getArgument(0).setId(19L);
            return 1;
        });

        String payload = "{\"action\":\"opened\",\"repository\":{\"full_name\":\"acme/demo\"},"
                + "\"issue\":{\"number\":42}}";
        service.receive("delivery-1", "issues", sign(payload, "secret"), payload);

        verify(issueTasks).insert(any(IssueInvestigationTask.class));
        verifyNoInteractions(reviewTasks, github);
        ArgumentCaptor<WebhookEvent> event = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(events).updateById(event.capture());
        assertThat(event.getValue().getStatus().name()).isEqualTo("DISPATCHED");
    }

    private static RepositoryTriggerRule rule() {
        RepositoryTriggerRule rule = new RepositoryTriggerRule();
        rule.setEnabled(true);
        rule.setEventKind(org.koaks.codereview.webhook.domain.WebhookEnums.EventKind.ISSUE);
        rule.setMode(org.koaks.codereview.webhook.domain.WebhookEnums.Mode.AUTO);
        rule.setAction("opened");
        rule.setEffort(ReviewEnums.Effort.MEDIUM);
        return rule;
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

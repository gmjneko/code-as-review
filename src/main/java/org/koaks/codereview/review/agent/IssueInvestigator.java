package org.koaks.codereview.review.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.ExecutionMode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Runs one Issue investigation over a materialized repository workspace. */
@Component
@RequiredArgsConstructor
public class IssueInvestigator {

    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(30);

    private final ReviewAgentFactory agents;
    private final PromptTemplates prompts;
    private final CodeReviewProperties properties;

    public IssueInvestigationReport investigate(long taskId, long userId, io.agentscope.core.model.Model model,
                                                TaskBudget budget, CancellationToken cancellation,
                                                Path codeRoot, Path scratchRoot, ExecutionMode mode,
                                                IssueInput input) {
        List<MiddlewareBase> middlewares = List.of(new BudgetMiddleware(budget));
        RuntimeContext context = RuntimeContext.builder().userId(Long.toString(userId))
                .sessionId("issue-" + taskId).build();
        String user = prompts.render(PromptTemplates.ISSUE_USER, Map.of(
                "issue_title", blank(input.title()),
                "issue_body", blank(input.body()),
                "issue_state", blank(input.state()),
                "issue_author", blank(input.author()),
                "issue_labels", blank(input.labels()),
                "issue_comments", blank(input.comments()),
                "requirement_background", blank(input.background()),
                "base_ref", blank(input.baseRef()),
                "base_sha", blank(input.baseSha())));
        try (HarnessAgent agent = agents.investigator(model, prompts.get(PromptTemplates.ISSUE_SYSTEM), codeRoot,
                scratchRoot.resolve("agent"), middlewares, properties.review().maxIters(), mode);
             var ignored = cancellation.register(() -> agent.interrupt(context))) {
            Msg reply = agent.call(List.of(new UserMessage(user)), IssueInvestigationReport.class, context)
                    .block(CALL_TIMEOUT);
            cancellation.throwIfCancelled();
            if (reply != null && reply.hasStructuredData()) {
                return reply.getStructuredData(IssueInvestigationReport.class);
            }
            return IssueInvestigationReport.fallback(reply == null ? "Agent returned no report." : reply.getTextContent());
        }
    }

    public record IssueInput(String title, String body, String state, String author, String labels,
                             String comments, String background, String baseRef, String baseSha) {
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}

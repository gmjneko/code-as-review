package org.koaks.codereview.rag.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.llm.factory.ChatModelFactory;
import org.koaks.codereview.llm.service.LlmModelConfigService;
import org.koaks.codereview.scm.github.GitHubClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RagVerifier {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    private final LlmModelConfigService modelConfigs;
    private final ChatModelFactory modelFactory;

    public MatchDecision verify(long userId, RagEnumsView source, RagEnumsView candidate, String evidence) {
        Model model = modelFactory.create(modelConfigs.resolve(userId, null, null));
        String prompt = """
                Determine whether two GitHub work items describe the same underlying problem or a direct fix relationship.
                Never infer facts outside the supplied records. Return only the structured result.
                For ISSUE to ISSUE, sameProblem must be true only for a duplicate report of the same problem.
                For ISSUE to PR or PR to ISSUE, relevant must be true only when the PR directly addresses the issue.

                SOURCE:
                %s

                CANDIDATE:
                %s

                RETRIEVAL EVIDENCE:
                %s
                """.formatted(source.render(), candidate.render(), evidence == null ? "(none)" : evidence);
        try (ReActAgent agent = ReActAgent.builder().name("rag-match-verifier")
                .sysPrompt("You are a strict GitHub Issue/PR association verifier. Do not use tools.")
                .model(model).toolkit(new Toolkit()).maxIters(1).build()) {
            RuntimeContext context = RuntimeContext.builder().userId(Long.toString(userId))
                    .sessionId("rag-verifier-" + System.nanoTime()).build();
            Msg reply = agent.call(List.of(new UserMessage(prompt)), MatchDecision.class, context).block(TIMEOUT);
            if (reply != null && reply.hasStructuredData()) return reply.getStructuredData(MatchDecision.class);
        } catch (RuntimeException ignored) {
            // Verification failures are treated as a safe non-match by the caller.
        }
        return new MatchDecision(false, false, 0d, "verification did not return a structured decision");
    }

    public record MatchDecision(boolean sameProblem, boolean relevant, double confidence, String reason) {}

    public record RagEnumsView(String type, String number, String title, String body, String state, String author) {
        public String render() {
            return "type=" + type + " #" + number + "\nstate=" + state + "\nauthor=" + author
                    + "\ntitle=" + title + "\nbody=" + body;
        }

        public static RagEnumsView issue(GitHubClient.GitHubIssue value) {
            return new RagEnumsView("ISSUE", Integer.toString(value.number()), value.title(), value.body(), value.state(),
                    value.user() == null ? "" : value.user().login());
        }

        public static RagEnumsView pr(GitHubClient.GitHubPullRequest value) {
            return new RagEnumsView("PR", Integer.toString(value.number()), value.title(), value.body(), value.state(),
                    value.user() == null ? "" : value.user().login());
        }
    }
}

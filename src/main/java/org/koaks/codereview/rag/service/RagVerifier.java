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
/**
 * 使用现有用户模型对召回候选做二次核验。
 *
 * <p>向量/BM25 只能说明文本相似，不能决定两个 Issue 是否真的重复。因此这里把完整的
 * 源对象和候选对象重新交给无工具 AgentScope Agent。任何超时、模型异常或结构化输出异常
 * 都按“不关联”处理，避免错误自动关联。</p>
 */
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
            // 核验失败按安全默认值处理，不能因为 Agent 临时异常而自动创建关系。
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

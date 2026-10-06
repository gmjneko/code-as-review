package org.koaks.codereview.review.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.comment.CommentCollector;
import org.koaks.codereview.review.comment.LineRelocator;
import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.review.diff.TokenEstimator;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reviews a whole change in one agent session: an optional plan, up to
 * {@link ReviewEnums.Effort#rounds()} review rounds each followed by a fact-check filter, then
 * line relocation. Round 2+ drops the plan (so it does not cap coverage) and shows the findings
 * confirmed so far; a round with no new confirmed finding ends the loop.
 */
@Slf4j
@Component
public class ChangeReviewer {

    private static final Duration REVIEW_CALL_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ANALYST_CALL_TIMEOUT = Duration.ofMinutes(5);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReviewAgentFactory agents;
    private final PromptTemplates prompts;
    private final RuleCatalog rules;
    private final CodeReviewProperties.Review limits;
    private final JsonMapper json = JsonMapper.builder().build();

    public ChangeReviewer(ReviewAgentFactory agents, PromptTemplates prompts, RuleCatalog rules,
                          CodeReviewProperties properties) {
        this.agents = agents;
        this.prompts = prompts;
        this.rules = rules;
        this.limits = properties.review();
    }

    /**
     * @param failure set when not even the first round completed
     * @param warning why a partially completed review stopped early; earlier findings are kept
     */
    public record Outcome(List<CandidateComment> comments, int roundsCompleted, String plan, String summary,
                          String failure, String warning) {
    }

    /** Receives findings as soon as they are final, so they can be stored while the review runs. */
    @FunctionalInterface
    public interface FindingsListener {

        /**
         * @param comments        findings not reported before, with their filter verdict and line numbers
         * @param roundsCompleted rounds finished so far
         */
        void onFindings(List<CandidateComment> comments, int roundsCompleted);
    }

    public Outcome review(TaskRuntime rt, List<FileDiff> files) {
        return review(rt, files, (comments, rounds) -> {
        });
    }

    public Outcome review(TaskRuntime rt, List<FileDiff> files, FindingsListener listener) {
        List<MiddlewareBase> middlewares = List.of(new BudgetMiddleware(rt.budget()));
        CommentCollector collector = new CommentCollector(limits.maxComments());
        Set<String> reviewPaths = new LinkedHashSet<>();
        files.forEach(f -> reviewPaths.add(f.path()));
        ReviewContext ctx = new ReviewContext(rt.taskId(), rt.diffsByPath(), reviewPaths, collector,
                new AtomicInteger(1));

        Map<String, String> common = new LinkedHashMap<>();
        common.put("change_files", renderOtherFiles(rt.contextFiles(), reviewPaths));
        common.put("diffs", renderDiffs(files, limits.maxPromptDiffTokens()));
        common.put("current_date_time", LocalDateTime.now().format(DATE_TIME));
        common.put("requirement_background", blankAs(rt.background(), "(none)"));
        common.put("system_rule", rules.ruleFor(files));

        int changedLines = files.stream().mapToInt(FileDiff::changedLines).sum();
        String plan = changedLines >= limits.planLineThreshold() ? plan(rt, middlewares, common) : "";

        List<CandidateComment> confirmed = new ArrayList<>();
        String summary = null;
        String stopReason = null;
        int rounds = 0;
        int reported = 0;
        for (int round = 1; round <= rt.effort().rounds(); round++) {
            rt.cancellation().throwIfCancelled();
            if (round > 1 && rt.budget().exceeded()) {
                stopReason = "token budget exhausted before round " + round;
                break;
            }
            ctx.round().set(round);
            int baseline = collector.size();
            Map<String, String> vars = new LinkedHashMap<>(common);
            vars.put("plan_guidance", round == 1 ? blankAs(plan, "(none)") : "(none)");
            vars.put("confirmed_comments", renderConfirmed(confirmed));
            try {
                summary = mainRound(rt, ctx, round, middlewares, prompts.render(PromptTemplates.MAIN_USER, vars));
            } catch (CancellationToken.CancelledException e) {
                throw e;
            } catch (RuntimeException e) {
                stopReason = "round " + round + " failed: " + rootMessage(e);
                log.warn("Task {}: {}", rt.taskId(), stopReason, e);
                break;
            }
            rounds = round;

            List<CandidateComment> fresh = collector.since(baseline);
            List<CandidateComment> kept = filter(rt, round, middlewares, fresh);
            confirmed.addAll(kept);
            report(rt, fresh, rounds, listener);
            reported = collector.size();
            if (kept.isEmpty() || collector.full()) {
                break;
            }
        }
        // A round that failed midway may have recorded findings that were never fact-checked.
        report(rt, collector.since(reported), rounds, listener);

        return rounds == 0
                ? new Outcome(collector.all(), 0, plan, summary, stopReason, null)
                : new Outcome(collector.all(), rounds, plan, summary, null, stopReason);
    }

    private static void report(TaskRuntime rt, List<CandidateComment> comments, int rounds, FindingsListener listener) {
        if (comments.isEmpty()) {
            return;
        }
        for (CandidateComment c : comments) {
            LineRelocator.locate(rt.diffsByPath().get(c.getPath()), c.getExistingCode()).ifPresent(r -> {
                c.setStartLine(r.startLine());
                c.setEndLine(r.endLine());
            });
        }
        listener.onFindings(comments, rounds);
    }

    private String plan(TaskRuntime rt, List<MiddlewareBase> middlewares, Map<String, String> vars) {
        ReActAgent planner = agents.analyst("review-planner", rt.model(),
                prompts.get(PromptTemplates.PLAN_SYSTEM), middlewares);
        RuntimeContext rc = runtimeContext(rt, "plan", null);
        try (var ignored = rt.cancellation().register(() -> planner.interrupt(rc))) {
            Msg reply = planner.call(List.of(new UserMessage(prompts.render(PromptTemplates.PLAN_USER, vars))), rc)
                    .block(ANALYST_CALL_TIMEOUT);
            rt.cancellation().throwIfCancelled();
            return reply == null ? "" : reply.getTextContent().strip();
        } catch (CancellationToken.CancelledException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Task {}: plan phase failed, continuing without plan: {}", rt.taskId(), rootMessage(e));
            return "";
        }
    }

    private String mainRound(TaskRuntime rt, ReviewContext ctx, int round, List<MiddlewareBase> middlewares,
                             String userPrompt) {
        HarnessAgent reviewer = agents.reviewer(rt.model(), prompts.get(PromptTemplates.MAIN_SYSTEM),
                rt.codeRoot(), rt.scratchRoot().resolve("agent"), middlewares, limits.maxIters());
        RuntimeContext rc = runtimeContext(rt, "main-r" + round, ctx);
        try (var ignored = rt.cancellation().register(() -> reviewer.interrupt(rc))) {
            Msg reply = reviewer.call(List.of(new UserMessage(userPrompt)), rc).block(REVIEW_CALL_TIMEOUT);
            rt.cancellation().throwIfCancelled();
            return reply == null ? null : reply.getTextContent();
        }
    }

    /**
     * Marks each fresh comment CONFIRMED or FILTERED and returns the confirmed ones. The checker
     * sees the complete diff of every commented file, even ones omitted from the review prompt,
     * because judging a comment without its subject diff would remove correct findings.
     */
    private List<CandidateComment> filter(TaskRuntime rt, int round, List<MiddlewareBase> middlewares,
                                          List<CandidateComment> fresh) {
        if (fresh.isEmpty()) {
            return List.of();
        }
        Set<String> remove = new HashSet<>();
        String reason = null;
        List<FileDiff> subjects = fresh.stream().map(CandidateComment::getPath).distinct()
                .map(rt.diffsByPath()::get).filter(Objects::nonNull).toList();
        ReActAgent checker = agents.analyst("review-filter", rt.model(),
                prompts.get(PromptTemplates.FILTER_SYSTEM), middlewares);
        RuntimeContext rc = runtimeContext(rt, "filter-r" + round, null);
        try (var ignored = rt.cancellation().register(() -> checker.interrupt(rc))) {
            String user = prompts.render(PromptTemplates.FILTER_USER,
                    Map.of("diff", renderDiffs(subjects, 0), "comments", renderCandidates(fresh)));
            Msg reply = checker.call(List.of(new UserMessage(user)), FilterResult.class, rc).block(ANALYST_CALL_TIMEOUT);
            rt.cancellation().throwIfCancelled();
            if (reply != null && reply.hasStructuredData()) {
                FilterResult result = reply.getStructuredData(FilterResult.class);
                if (result.removeIds() != null) {
                    remove.addAll(result.removeIds());
                }
                reason = result.analysis() == null ? null : String.join("\n", result.analysis());
            }
        } catch (CancellationToken.CancelledException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Task {}: filter failed, keeping all comments: {}", rt.taskId(), rootMessage(e));
        }

        List<CandidateComment> kept = new ArrayList<>();
        for (CandidateComment c : fresh) {
            if (remove.contains(c.getId())) {
                c.setStatus(ReviewEnums.CommentStatus.FILTERED);
                c.setFilterReason(reason);
            } else {
                c.setStatus(ReviewEnums.CommentStatus.CONFIRMED);
                kept.add(c);
            }
        }
        return kept;
    }

    private static RuntimeContext runtimeContext(TaskRuntime rt, String phase, ReviewContext ctx) {
        RuntimeContext.Builder b = RuntimeContext.builder()
                .userId(Long.toString(rt.userId()))
                .sessionId("review-" + rt.taskId() + ":" + phase);
        if (ctx != null) {
            b.put(ReviewContext.class, ctx);
        }
        return b.build();
    }

    /**
     * Inlines diffs in order until {@code tokenBudget} is spent; the rest are listed with
     * {@code omitted="true"} for the agent to fetch via {@code read_file_diff}. A budget of 0 or
     * less inlines everything.
     */
    static String renderDiffs(List<FileDiff> files, int tokenBudget) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (FileDiff f : files) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            int cost = TokenEstimator.estimate(f.hunksText());
            boolean inline = tokenBudget <= 0 || used + cost <= tokenBudget;
            if (inline) {
                used += cost;
                sb.append("<file path=\"").append(f.path()).append("\">\n").append(f.hunksText()).append("\n</file>");
            } else {
                sb.append("<file path=\"").append(f.path()).append("\" omitted=\"true\" changes=\"+")
                        .append(f.additions()).append("/-").append(f.deletions()).append("\"/>");
            }
        }
        return sb.toString();
    }

    static String renderOtherFiles(List<FileDiff> all, Set<String> reviewPaths) {
        StringBuilder sb = new StringBuilder();
        for (FileDiff f : all) {
            if (f.binary() || reviewPaths.contains(f.path())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(f.changeType()).append("   ").append(f.path())
                    .append(" (+").append(f.additions()).append("/-").append(f.deletions()).append(')');
        }
        return sb.isEmpty() ? "(none)" : sb.toString();
    }

    static String renderConfirmed(List<CandidateComment> confirmed) {
        if (confirmed.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder("Do not report these again; look for issues they do not cover.\n");
        for (CandidateComment c : confirmed) {
            sb.append("- [").append(c.getSeverity()).append("] ").append(c.getPath()).append(": ")
                    .append(c.getContent().replace('\n', ' ')).append('\n');
        }
        return sb.toString().strip();
    }

    private String renderCandidates(List<CandidateComment> comments) {
        List<Map<String, Object>> rows = comments.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("path", c.getPath());
            m.put("category", c.getCategory());
            m.put("severity", c.getSeverity());
            m.put("content", c.getContent());
            m.put("existing_code", c.getExistingCode());
            return m;
        }).toList();
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
    }

    private static String blankAs(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

}

package org.koaks.codereview.review.agent;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.state.InMemoryAgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.diff.DiffParser;
import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.review.domain.ReviewEnums;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.koaks.codereview.review.agent.ScriptedModel.text;
import static org.koaks.codereview.review.agent.ScriptedModel.tool;

class ChangeReviewerTest {

    private static final String DIFF = """
            diff --git a/src/Calc.java b/src/Calc.java
            --- a/src/Calc.java
            +++ b/src/Calc.java
            @@ -1,3 +1,4 @@
             class Calc {
            -    int div(int a, int b) { return a * b; }
            +    int div(int a, int b) { return a / b; }
            +    int unused = 42;
             }
            diff --git a/src/Old.java b/src/Old.java
            deleted file mode 100644
            --- a/src/Old.java
            +++ /dev/null
            @@ -1 +0,0 @@
            -class Old {}
            """;

    @TempDir
    Path tmp;

    private Path repo;
    private ChangeReviewer reviewer;
    private List<FileDiff> diffs;

    @BeforeEach
    void setUp() throws Exception {
        repo = Files.createDirectories(tmp.resolve("repo/src"));
        Files.writeString(repo.resolve("Calc.java"),
                "class Calc {\n    int div(int a, int b) { return a / b; }\n    int unused = 42;\n}\n");
        repo = repo.getParent();
        CodeReviewProperties props = new CodeReviewProperties(tmp, List.of(tmp), null,
                new CodeReviewProperties.Review("medium", 60000, 30000, 0, 50, 1, 30, 60), null, null);
        ReviewAgentFactory factory = new ReviewAgentFactory(new InMemoryAgentStateStore(), new ReviewTools());
        reviewer = new ChangeReviewer(factory, new PromptTemplates(), new RuleCatalog(), props);
        diffs = DiffParser.parse(DIFF);
    }

    @Test
    void plansReviewsFiltersAndStopsWhenARoundAddsNothing() {
        ScriptedModel model = new ScriptedModel(this::reviewScript);
        TaskBudget budget = new TaskBudget(0);

        ChangeReviewer.Outcome outcome = reviewer.review(runtime(model, budget, new CancellationToken()),
                List.of(diffs.getFirst()));

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.warning()).isNull();
        assertThat(outcome.plan()).startsWith("Summary: division change");
        assertThat(outcome.roundsCompleted()).isEqualTo(2);
        assertThat(outcome.summary()).contains("Nothing new");

        List<CandidateComment> comments = outcome.comments();
        assertThat(comments).extracting(CandidateComment::getId).containsExactly("c-0", "c-1");
        CandidateComment kept = comments.get(0);
        assertThat(kept.getStatus()).isEqualTo(ReviewEnums.CommentStatus.CONFIRMED);
        assertThat(kept.getStartLine()).isEqualTo(2);
        assertThat(kept.getCategory()).isEqualTo("bug");
        CandidateComment dropped = comments.get(1);
        assertThat(dropped.getStatus()).isEqualTo(ReviewEnums.CommentStatus.FILTERED);
        assertThat(dropped.getFilterReason()).contains("c-1 contradicted");
        assertThat(dropped.getStartLine()).isEqualTo(3);

        ScriptedModel.Request firstMain = model.requests.stream().filter(r -> isMain(r)).findFirst().orElseThrow();
        assertThat(firstMain.toolNames()).contains("read_file", "grep_files", "glob_files", "read_file_diff",
                "code_comment").doesNotContain("write_file", "edit_file", "execute", "web_fetch");
        assertThat(firstMain.allText()).contains("<file path=\"src/Calc.java\">")
                .contains("DELETED   src/Old.java").contains("Summary: division change");

        List<String> toolOutputs = model.requests.stream().flatMap(r -> r.toolResults().stream())
                .map(ChangeReviewerTest::output).distinct().toList();
        assertThat(toolOutputs).anyMatch(o -> o.contains("return a / b;"));
        assertThat(toolOutputs).anyMatch(o -> o.contains("Rejected: 'src/Old.java' is not in <review_files>"));
        assertThat(toolOutputs).anyMatch(o -> o.contains("Path traversal not allowed")
                || o.contains("outside") || o.contains("Error"));

        ScriptedModel.Request secondRound = model.requests.stream()
                .filter(r -> isMain(r) && r.allText().contains("Do not report these again")).findFirst().orElseThrow();
        assertThat(secondRound.allText()).contains("Possible division by zero").contains("Review Plan\n(none)")
                .doesNotContain("Field is never used");
        assertThat(budget.total()).isPositive();
    }

    @Test
    void reportsEachRoundsFindingsOnceTheyAreFactChecked() {
        ScriptedModel model = new ScriptedModel(this::reviewScript);
        List<List<String>> batches = new ArrayList<>();
        List<Integer> rounds = new ArrayList<>();

        ChangeReviewer.Outcome outcome = reviewer.review(runtime(model, new TaskBudget(0), new CancellationToken()),
                List.of(diffs.getFirst()), (comments, completed) -> {
                    assertThat(comments).allSatisfy(c -> assertThat(c.getStatus()).isNotNull());
                    batches.add(comments.stream().map(c -> c.getId() + "@" + c.getStartLine()).toList());
                    rounds.add(completed);
                });

        // Round 2 adds nothing, so only round 1 reports; every finding is reported exactly once.
        assertThat(batches).containsExactly(List.of("c-0@2", "c-1@3"));
        assertThat(rounds).containsExactly(1);
        assertThat(outcome.comments()).hasSize(2);
    }

    @Test
    void inlineBudgetOmitsLargeDiffsAndFilterStillSeesThem() {
        String omitted = ChangeReviewer.renderDiffs(diffs, 1);
        assertThat(omitted).contains("<file path=\"src/Calc.java\" omitted=\"true\" changes=\"+2/-1\"/>");
        assertThat(ChangeReviewer.renderDiffs(diffs, 0)).contains("return a / b;");
    }

    @Test
    void failsWhenTheBudgetIsSpentBeforeTheFirstRound() {
        ScriptedModel model = new ScriptedModel(this::reviewScript);
        ChangeReviewer.Outcome outcome = reviewer.review(runtime(model, new TaskBudget(50), new CancellationToken()),
                List.of(diffs.getFirst()));

        assertThat(outcome.roundsCompleted()).isZero();
        assertThat(outcome.failure()).contains("round 1 failed").contains("token budget exhausted");
    }

    @Test
    void cancellationAbortsTheReview() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        ScriptedModel model = new ScriptedModel(r -> text("unused"));
        assertThatThrownBy(() -> reviewer.review(runtime(model, new TaskBudget(0), token), List.of(diffs.getFirst())))
                .isInstanceOf(CancellationToken.CancelledException.class);
    }

    private ContentBlock reviewScript(ScriptedModel.Request r) {
        String all = r.allText();
        if (all.contains("fact-checker for code review comments")) {
            return tool("generate_response", Map.of("response", Map.of(
                    "analysis", List.of("c-1 contradicted: the field is used"), "removeIds", List.of("c-1"))));
        }
        if (all.contains("expert in code review task planning")) {
            return text("Summary: division change\n\nIssues\n\n1. [high] division by zero\n   → read_file src/Calc.java — check callers");
        }
        if (all.contains("Do not report these again")) {
            return text("Nothing new to report.");
        }
        return switch (r.toolResults().size()) {
            case 0 -> tool("read_file", Map.of("path", "src/Calc.java"));
            case 1 -> tool("read_file", Map.of("path", "../../../etc/hosts"));
            case 2 -> tool("code_comment", Map.of("path", "src/Calc.java",
                    "content", "Possible division by zero when b is 0.",
                    "existing_code", "int div(int a, int b) { return a / b; }",
                    "category", "BUG", "severity", "high"));
            case 3 -> tool("code_comment", Map.of("path", "./src/Calc.java", "content", "Field is never used.",
                    "existing_code", "+    int unused = 42;", "category", "weird", "severity", "low"));
            case 4 -> tool("code_comment", Map.of("path", "src/Old.java", "content", "x",
                    "existing_code", "class Old {}", "category", "bug", "severity", "low"));
            default -> text("Reviewed src/Calc.java.");
        };
    }

    private static boolean isMain(ScriptedModel.Request r) {
        return r.allText().contains("You are a code review assistant");
    }

    private static String output(ToolResultBlock block) {
        StringBuilder sb = new StringBuilder();
        block.getOutput().forEach(b -> sb.append(b instanceof io.agentscope.core.message.TextBlock t ? t.getText() : b));
        return sb.toString();
    }

    private TaskRuntime runtime(ScriptedModel model, TaskBudget budget, CancellationToken token) {
        Map<String, FileDiff> byPath = new LinkedHashMap<>();
        diffs.forEach(d -> byPath.put(d.path(), d));
        return new TaskRuntime(7L, 3L, model, budget, token, byPath, diffs, "Fix the div helper",
                ReviewEnums.Effort.HIGH, repo, Files.exists(tmp.resolve("scratch")) ? tmp.resolve("scratch")
                : createDir(tmp.resolve("scratch")));
    }

    private static Path createDir(Path p) {
        try {
            return Files.createDirectories(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package org.koaks.codereview.webhook.service;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.review.agent.IssueInvestigationReport;
import org.koaks.codereview.scm.github.GitHubClient;
import org.koaks.codereview.webhook.domain.IssueInvestigationTask;
import org.springframework.stereotype.Component;

import java.util.List;

/** Publishes one idempotent diagnostic comment for an Issue task. */
@Component
@RequiredArgsConstructor
public class IssueResultPublisher {

    private final GitHubClient github;

    public Published publish(IssueInvestigationTask task, CodeRepository repo,
                             IssueInvestigationReport report, String failure) {
        String marker = "<!-- code-as-review:issue-task:" + task.getId() + " -->";
        String body = render(marker, task, report, failure);
        List<GitHubClient.GitHubIssueComment> comments = github.issueComments(repo.getExternalFullName(),
                task.getIssueNumber(), repo.getCredentialId(), task.getUserId());
        GitHubClient.GitHubIssueComment existing = comments.stream()
                .filter(c -> c.body() != null && c.body().contains(marker)).findFirst().orElse(null);
        String id;
        if (existing != null && existing.id() != null) {
            id = github.updateIssueComment(repo.getExternalFullName(), existing.id(), body,
                    repo.getCredentialId(), task.getUserId());
        } else {
            id = github.createIssueComment(repo.getExternalFullName(), task.getIssueNumber(), body,
                    repo.getCredentialId(), task.getUserId());
        }
        return new Published(id, body);
    }

    private static String render(String marker, IssueInvestigationTask task,
                                 IssueInvestigationReport report, String failure) {
        StringBuilder out = new StringBuilder(marker).append("\n## Code as Review Issue 调查\n\n");
        out.append("- 任务：#").append(task.getId()).append("\n")
                .append("- 基线：").append(task.getBaseRef() == null ? "-" : task.getBaseRef())
                .append(task.getBaseSha() == null ? "" : " (" + task.getBaseSha() + ")").append("\n");
        if (failure != null && !failure.isBlank()) {
            out.append("- 状态：FAILED\n\n调查执行失败：").append(failure);
            return out.toString();
        }
        out.append("- 状态：").append(value(report == null ? null : report.reproductionStatus(), "UNKNOWN"))
                .append("\n\n");
        section(out, "摘要", report == null ? null : report.summary());
        listSection(out, "复现步骤", report == null ? List.of() : report.reproductionSteps());
        listSection(out, "观察结果", report == null ? List.of() : report.observations());
        section(out, "可能根因", report == null ? null : report.rootCause());
        section(out, "修复建议", report == null ? null : report.suggestedFix());
        return out.toString();
    }

    private static void section(StringBuilder out, String title, String content) {
        out.append("### ").append(title).append("\n").append(value(content, "（无）")).append("\n\n");
    }

    private static void listSection(StringBuilder out, String title, List<String> values) {
        out.append("### ").append(title).append("\n");
        if (values == null || values.isEmpty()) out.append("（无）\n\n");
        else {
            values.forEach(v -> out.append("- ").append(v).append("\n"));
            out.append('\n');
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public record Published(String commentId, String markdown) {
    }
}

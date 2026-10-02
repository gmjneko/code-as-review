package org.koaks.codereview.review.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.diff.FileDiff;

import java.util.Locale;
import java.util.Set;

/**
 * Review-specific tools. Reading and searching the repository is left to the harness filesystem
 * tools; these two only expose the diff set and collect findings. Per-task state arrives through
 * the injected {@link ReviewContext}, so one instance serves every conversation.
 */
public class ReviewTools {

    static final Set<String> CATEGORIES =
            Set.of("bug", "security", "performance", "maintainability", "test", "style", "documentation", "other");
    static final Set<String> SEVERITIES = Set.of("critical", "high", "medium", "low");

    @Tool(name = "read_file_diff", readOnly = true,
            description = "Return the unified diff of a file changed in this update. Use it for files listed "
                    + "without an inline diff and to check how related files changed.")
    public String readFileDiff(
            ReviewContext ctx,
            @ToolParam(name = "path", description = "Repository-relative path of a changed file") String path) {
        FileDiff diff = ctx.diffsByPath().get(normalise(path));
        if (diff == null) {
            return "No diff for '" + path + "'. Changed files: " + String.join(", ", ctx.diffsByPath().keySet());
        }
        if (diff.binary()) {
            return "'" + path + "' is a binary file; no textual diff.";
        }
        return "File: " + diff.path() + " (" + diff.changeType() + ", +" + diff.additions() + "/-" + diff.deletions()
                + ")\n" + diff.hunksText();
    }

    @Tool(name = "code_comment",
            description = "Report one confirmed code issue in a file under review. The comment is anchored by "
                    + "matching 'existing_code' against the diff, so it must be one or several consecutive lines "
                    + "copied exactly from the added lines of the diff (without the leading '+'). "
                    + "Call once per issue.")
    public String codeComment(
            ReviewContext ctx,
            @ToolParam(name = "path", description = "Repository-relative path of the file in <review_files>") String path,
            @ToolParam(name = "content", description = "Brief description of the issue and how to fix it") String content,
            @ToolParam(name = "existing_code", description = "Exact newly added code line(s) the comment refers to")
            String existingCode,
            @ToolParam(name = "category", description = "One of: bug, security, performance, maintainability, "
                    + "test, style, documentation, other") String category,
            @ToolParam(name = "severity", description = "One of: critical, high, medium, low") String severity,
            @ToolParam(name = "suggestion_code", required = false,
                    description = "Suggested replacement code, in the same style") String suggestionCode) {
        String file = normalise(path);
        if (!ctx.reviewPaths().contains(file)) {
            return "Rejected: '" + path + "' is not in <review_files>. Comment only on: "
                    + String.join(", ", ctx.reviewPaths());
        }
        if (content == null || content.isBlank()) {
            return "Rejected: 'content' must not be empty.";
        }
        if (existingCode == null || existingCode.isBlank()) {
            return "Rejected: 'existing_code' must quote the added line(s) the comment refers to.";
        }
        CandidateComment stored = ctx.comments().add(file, content.strip(), existingCode, blankToNull(suggestionCode),
                pick(category, CATEGORIES, "other"), pick(severity, SEVERITIES, "medium"), ctx.round().get());
        if (stored == null) {
            return "Rejected: the comment limit for this review has been reached. Finish the review now.";
        }
        return "Recorded comment " + stored.getId() + " on " + file + ".";
    }

    static String normalise(String path) {
        if (path == null) {
            return "";
        }
        String p = path.strip().replace('\\', '/');
        while (p.startsWith("./") || p.startsWith("/")) {
            p = p.startsWith("./") ? p.substring(2) : p.substring(1);
        }
        return p;
    }

    private static String pick(String value, Set<String> allowed, String fallback) {
        String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return allowed.contains(v) ? v : fallback;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

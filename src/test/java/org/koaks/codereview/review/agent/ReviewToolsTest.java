package org.koaks.codereview.review.agent;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.review.comment.CommentCollector;
import org.koaks.codereview.review.diff.DiffParser;
import org.koaks.codereview.review.diff.FileDiff;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewToolsTest {

    private final ReviewTools tools = new ReviewTools();
    private final FileDiff diff = DiffParser.parse("""
            diff --git a/a/X.java b/a/X.java
            --- a/a/X.java
            +++ b/a/X.java
            @@ -1 +1 @@
            -old
            +new
            """).getFirst();
    private final CommentCollector collector = new CommentCollector(1);
    private final ReviewContext ctx = new ReviewContext(1, Map.of("a/X.java", diff), Set.of("a/X.java"), collector,
            new AtomicInteger(2));

    @Test
    void readsDiffsOfChangedFiles() {
        assertThat(tools.readFileDiff(ctx, "/a/X.java")).contains("MODIFIED").contains("+new");
        assertThat(tools.readFileDiff(ctx, "b.txt")).startsWith("No diff for 'b.txt'");
    }

    @Test
    void validatesAndCapsComments() {
        assertThat(tools.codeComment(ctx, "other.java", "c", "x", "bug", "high", null)).startsWith("Rejected");
        assertThat(tools.codeComment(ctx, "a/X.java", " ", "x", "bug", "high", null)).startsWith("Rejected");
        assertThat(tools.codeComment(ctx, "a/X.java", "c", "", "bug", "high", null)).startsWith("Rejected");

        assertThat(tools.codeComment(ctx, "./a/X.java", "Bad name", "new", "Naming", "URGENT", " "))
                .isEqualTo("Recorded comment c-0 on a/X.java.");
        var stored = collector.all().getFirst();
        assertThat(stored.getCategory()).isEqualTo("other");
        assertThat(stored.getSeverity()).isEqualTo("medium");
        assertThat(stored.getSuggestionCode()).isNull();
        assertThat(stored.getRound()).isEqualTo(2);

        assertThat(tools.codeComment(ctx, "a/X.java", "again", "new", "bug", "low", null))
                .contains("limit for this review has been reached");
    }
}

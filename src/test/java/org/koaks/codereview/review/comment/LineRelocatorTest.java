package org.koaks.codereview.review.comment;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.review.diff.DiffParser;
import org.koaks.codereview.review.diff.FileDiff;

import static org.assertj.core.api.Assertions.assertThat;

class LineRelocatorTest {

    private final FileDiff diff = DiffParser.parse("""
            diff --git a/S.java b/S.java
            --- a/S.java
            +++ b/S.java
            @@ -1,5 +1,7 @@
             class S {
            -  int a;
            +  int a = 0;
            +
            +  void run() { a++; }
               int b;
               int a = 0;
             }
            """).getFirst();

    @Test
    void matchesMultiLineSnippetIgnoringIndentAndBlankLines() {
        assertThat(LineRelocator.locate(diff, "+int a = 0;\n\nvoid run() { a++; }"))
                .contains(new LineRelocator.Range(2, 4));
    }

    @Test
    void prefersWindowsThatTouchAddedLines() {
        assertThat(LineRelocator.locate(diff, "int a = 0;")).contains(new LineRelocator.Range(2, 2));
    }

    @Test
    void fallsBackToFirstLineContainmentAndThenEmpty() {
        assertThat(LineRelocator.locate(diff, "a++;\nsomething the model made up"))
                .contains(new LineRelocator.Range(4, 4));
        assertThat(LineRelocator.locate(diff, "not in diff")).isEmpty();
        assertThat(LineRelocator.locate(diff, "  ")).isEmpty();
    }
}

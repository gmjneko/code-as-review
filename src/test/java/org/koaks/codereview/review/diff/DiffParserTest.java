package org.koaks.codereview.review.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffParserTest {

    @Test
    void parsesModifiedFileWithLineNumbers() {
        String diff = """
                diff --git a/src/App.java b/src/App.java
                index 1111111..2222222 100644
                --- a/src/App.java
                +++ b/src/App.java
                @@ -10,4 +10,4 @@ class App {
                     int a = 1;
                -    int b = 2;
                +    int b = 3;
                +    int c = 4;
                -- removed comment line
                     return;
                """;
        List<FileDiff> files = DiffParser.parse(diff);

        assertThat(files).hasSize(1);
        FileDiff f = files.getFirst();
        assertThat(f.path()).isEqualTo("src/App.java");
        assertThat(f.changeType()).isEqualTo(FileDiff.ChangeType.MODIFIED);
        assertThat(f.additions()).isEqualTo(2);
        assertThat(f.deletions()).isEqualTo(2);
        List<FileDiff.Line> lines = f.hunks().getFirst().lines();
        assertThat(lines.get(2)).isEqualTo(new FileDiff.Line(FileDiff.Line.Kind.ADDED, 0, 11, "    int b = 3;"));
        assertThat(lines.get(4)).isEqualTo(new FileDiff.Line(FileDiff.Line.Kind.REMOVED, 12, 0, "- removed comment line"));
        assertThat(lines.get(5).newLine()).isEqualTo(13);
        assertThat(f.hunksText()).startsWith("@@ -10,4 +10,4 @@").doesNotContain("index 1111111");
    }

    @Test
    void parsesAddedDeletedRenamedAndBinaryFiles() {
        String diff = """
                diff --git a/new.txt b/new.txt
                new file mode 100644
                index 0000000..3b18e51
                --- /dev/null
                +++ b/new.txt
                @@ -0,0 +1 @@
                +hello
                diff --git a/old.txt b/old.txt
                deleted file mode 100644
                index 3b18e51..0000000
                --- a/old.txt
                +++ /dev/null
                @@ -1 +0,0 @@
                -bye
                diff --git a/a/Old.java b/a/New.java
                similarity index 90%
                rename from a/Old.java
                rename to a/New.java
                index 1..2 100644
                --- a/a/Old.java
                +++ b/a/New.java
                @@ -1 +1 @@
                -class Old {}
                +class New {}
                diff --git a/logo.png b/logo.png
                index 1..2 100644
                Binary files a/logo.png and b/logo.png differ
                """;
        List<FileDiff> files = DiffParser.parse(diff);

        assertThat(files).extracting(FileDiff::changeType).containsExactly(
                FileDiff.ChangeType.ADDED, FileDiff.ChangeType.DELETED, FileDiff.ChangeType.RENAMED,
                FileDiff.ChangeType.MODIFIED);
        assertThat(files.get(0).path()).isEqualTo("new.txt");
        assertThat(files.get(1).path()).isEqualTo("old.txt");
        assertThat(files.get(2).oldPath()).isEqualTo("a/Old.java");
        assertThat(files.get(2).newPath()).isEqualTo("a/New.java");
        assertThat(files.get(3).binary()).isTrue();
        assertThat(files.get(3).path()).isEqualTo("logo.png");
    }

    @Test
    void decodesQuotedPaths() {
        assertThat(DiffParser.unquote("\"a/caf\\303\\251 x.txt\"")).isEqualTo("a/caf\u00e9 x.txt");
        assertThat(DiffParser.unquote("\"a/tab\\there\"")).isEqualTo("a/tab\there");
        assertThat(DiffParser.unquote("b/plain.txt\t")).isEqualTo("b/plain.txt");
        assertThat(DiffParser.pathsFromGitHeader("a/dir with space/f.txt b/dir with space/f.txt"))
                .containsExactly("dir with space/f.txt", "dir with space/f.txt");
    }

    @Test
    void emptyInputYieldsNoFiles() {
        assertThat(DiffParser.parse("")).isEmpty();
        assertThat(DiffParser.parse(null)).isEmpty();
    }
}

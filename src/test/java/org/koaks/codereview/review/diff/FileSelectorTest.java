package org.koaks.codereview.review.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSelectorTest {

    private final FileSelector selector = FileSelector.fromClasspath(100);

    @Test
    void zeroDirectoryDoubleStarMatchesRootFiles() {
        GlobMatcher m = GlobMatcher.of(List.of("**/*.java", "src/**/gen/**"), false);
        assertThat(m.matches("Foo.java")).isTrue();
        assertThat(m.matches("a/b/Foo.java")).isTrue();
        assertThat(m.matches("src/gen/x.txt")).isTrue();
        assertThat(m.matches("src/a/gen/b/x.txt")).isTrue();
        assertThat(m.matches("Foo.kt")).isFalse();
    }

    @Test
    void selectsSourceFilesAndExplainsExclusions() {
        List<FileDiff> diffs = List.of(
                diff("src/main/java/App.java", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+x"),
                diff("src/test/java/AppTest.java", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+x"),
                diff(".env", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+KEY=1"),
                diff("config/.ssh/id_rsa", FileDiff.ChangeType.ADDED, false, "@@ -0,0 +1 @@\n+k"),
                diff("logo.png", FileDiff.ChangeType.MODIFIED, true, ""),
                diff("notes.docx", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+x"),
                diff("web/package-lock.json", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+x"),
                diff("src/Gone.java", FileDiff.ChangeType.DELETED, false, "@@ -1 +0,0 @@\n-x"),
                diff("src/Huge.java", FileDiff.ChangeType.MODIFIED, false, "@@ -1 +1 @@\n+" + "x".repeat(1000)));

        FileSelector.Selection s = selector.select(diffs);

        assertThat(s.reviewable()).extracting(FileDiff::path).containsExactly("src/main/java/App.java");
        assertThat(s.context()).extracting(FileDiff::path).containsExactly("src/main/java/App.java", "src/Gone.java");
        assertThat(s.excluded()).containsEntry("src/test/java/AppTest.java", FileSelector.Reason.DEFAULT_PATH)
                .containsEntry(".env", FileSelector.Reason.SECRET)
                .containsEntry("config/.ssh/id_rsa", FileSelector.Reason.SECRET)
                .containsEntry("logo.png", FileSelector.Reason.BINARY)
                .containsEntry("notes.docx", FileSelector.Reason.EXTENSION)
                .containsEntry("web/package-lock.json", FileSelector.Reason.DEFAULT_PATH)
                .containsEntry("src/Gone.java", FileSelector.Reason.DELETED)
                .containsEntry("src/Huge.java", FileSelector.Reason.TOO_LARGE);
    }

    static FileDiff diff(String path, FileDiff.ChangeType type, boolean binary, String hunks) {
        List<FileDiff.Hunk> hunkList = hunks.isEmpty() ? List.of()
                : List.of(new FileDiff.Hunk(1, 1, 1, 1, List.of()));
        return new FileDiff(path, path, type, binary, hunkList, hunks, 1, 0);
    }
}

package org.koaks.codereview.review.diff;

import java.util.List;

/**
 * One file of a unified diff.
 *
 * @param hunksText the hunk section ({@code @@ ... @@} onwards) exactly as git printed it
 */
public record FileDiff(
        String oldPath,
        String newPath,
        ChangeType changeType,
        boolean binary,
        List<Hunk> hunks,
        String hunksText,
        int additions,
        int deletions) {

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED
    }

    /** The path a reviewer refers to: the new path, or the old one for deletions. */
    public String path() {
        return changeType == ChangeType.DELETED ? oldPath : newPath;
    }

    public int changedLines() {
        return additions + deletions;
    }

    public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<Line> lines) {
    }

    /**
     * @param oldLine line number in the old file, or 0 for added lines
     * @param newLine line number in the new file, or 0 for removed lines
     */
    public record Line(Kind kind, int oldLine, int newLine, String content) {

        public enum Kind {
            CONTEXT,
            ADDED,
            REMOVED
        }
    }
}

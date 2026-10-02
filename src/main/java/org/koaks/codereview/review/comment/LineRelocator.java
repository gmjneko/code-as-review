package org.koaks.codereview.review.comment;

import org.koaks.codereview.review.diff.FileDiff;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps a comment's {@code existing_code} snippet to line numbers in the new file by sliding it
 * over the new side of each hunk. Matching ignores leading/trailing whitespace and a leading
 * {@code +} the model may have copied from the diff. Windows touching added lines win over
 * windows that only cover context.
 */
public final class LineRelocator {

    public record Range(int startLine, int endLine) {
    }

    private LineRelocator() {
    }

    public static Optional<Range> locate(FileDiff diff, String existingCode) {
        List<String> needle = normalise(existingCode);
        if (needle.isEmpty() || diff == null) {
            return Optional.empty();
        }
        Range contextOnly = null;
        for (FileDiff.Hunk hunk : diff.hunks()) {
            List<FileDiff.Line> newSide = hunk.lines().stream()
                    .filter(l -> l.kind() != FileDiff.Line.Kind.REMOVED)
                    .filter(l -> !l.content().isBlank())
                    .toList();
            for (int i = 0; i + needle.size() <= newSide.size(); i++) {
                boolean match = true;
                boolean touchesAdded = false;
                for (int j = 0; j < needle.size(); j++) {
                    FileDiff.Line line = newSide.get(i + j);
                    if (!line.content().strip().equals(needle.get(j))) {
                        match = false;
                        break;
                    }
                    touchesAdded |= line.kind() == FileDiff.Line.Kind.ADDED;
                }
                if (!match) {
                    continue;
                }
                Range range = new Range(newSide.get(i).newLine(), newSide.get(i + needle.size() - 1).newLine());
                if (touchesAdded) {
                    return Optional.of(range);
                }
                if (contextOnly == null) {
                    contextOnly = range;
                }
            }
        }
        if (contextOnly != null) {
            return Optional.of(contextOnly);
        }
        return locateFirstLine(diff, needle.getFirst());
    }

    /** Fallback when the full snippet does not match: the first added line containing its first line. */
    private static Optional<Range> locateFirstLine(FileDiff diff, String firstLine) {
        for (FileDiff.Hunk hunk : diff.hunks()) {
            for (FileDiff.Line line : hunk.lines()) {
                if (line.kind() == FileDiff.Line.Kind.ADDED && line.content().strip().contains(firstLine)) {
                    return Optional.of(new Range(line.newLine(), line.newLine()));
                }
            }
        }
        return Optional.empty();
    }

    static List<String> normalise(String snippet) {
        List<String> out = new ArrayList<>();
        if (snippet == null) {
            return out;
        }
        for (String raw : snippet.split("\\R")) {
            String line = raw;
            if (line.startsWith("+") && !line.startsWith("++")) {
                line = line.substring(1);
            }
            line = line.strip();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }
}

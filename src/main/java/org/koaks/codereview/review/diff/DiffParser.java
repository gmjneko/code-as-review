package org.koaks.codereview.review.diff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code git diff} output (including {@code --no-index} output) into {@link FileDiff}s. */
public final class DiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");
    private static final String DEV_NULL = "/dev/null";

    private DiffParser() {
    }

    public static List<FileDiff> parse(String diff) {
        List<FileDiff> files = new ArrayList<>();
        if (diff == null || diff.isEmpty()) {
            return files;
        }
        Builder current = null;
        Hunk hunk = null;
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("diff --git ")) {
                if (current != null) {
                    files.add(current.build());
                }
                current = new Builder(line.substring("diff --git ".length()));
                hunk = null;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (hunk == null) {
                if (parseHeaderLine(current, line)) {
                    continue;
                }
            }
            Matcher m = HUNK_HEADER.matcher(line);
            if (m.matches()) {
                hunk = new Hunk(Integer.parseInt(m.group(1)), count(m.group(2)),
                        Integer.parseInt(m.group(3)), count(m.group(4)));
                current.hunks.add(hunk);
                current.text.append(line).append('\n');
                continue;
            }
            if (hunk == null || line.startsWith("\\")) {
                if (hunk != null) {
                    current.text.append(line).append('\n');
                }
                continue;
            }
            char marker = line.isEmpty() ? ' ' : line.charAt(0);
            if ((line.isEmpty() && hunk.exhausted()) || (marker != '+' && marker != '-' && marker != ' ')) {
                continue;
            }
            String content = line.isEmpty() ? "" : line.substring(1);
            switch (marker) {
                case '+' -> {
                    hunk.lines.add(new FileDiff.Line(FileDiff.Line.Kind.ADDED, 0, hunk.nextNew++, content));
                    current.additions++;
                }
                case '-' -> {
                    hunk.lines.add(new FileDiff.Line(FileDiff.Line.Kind.REMOVED, hunk.nextOld++, 0, content));
                    current.deletions++;
                }
                default -> hunk.lines.add(new FileDiff.Line(FileDiff.Line.Kind.CONTEXT, hunk.nextOld++, hunk.nextNew++, content));
            }
            current.text.append(line).append('\n');
        }
        if (current != null) {
            files.add(current.build());
        }
        return files;
    }

    private static boolean parseHeaderLine(Builder b, String line) {
        if (line.startsWith("--- ")) {
            b.oldPath = stripPrefix(unquote(line.substring(4)), "a/");
        } else if (line.startsWith("+++ ")) {
            b.newPath = stripPrefix(unquote(line.substring(4)), "b/");
        } else if (line.startsWith("rename from ")) {
            b.oldPath = unquote(line.substring("rename from ".length()));
            b.renamed = true;
        } else if (line.startsWith("rename to ")) {
            b.newPath = unquote(line.substring("rename to ".length()));
            b.renamed = true;
        } else if (line.startsWith("copy from ")) {
            b.oldPath = unquote(line.substring("copy from ".length()));
            b.copied = true;
        } else if (line.startsWith("copy to ")) {
            b.newPath = unquote(line.substring("copy to ".length()));
            b.copied = true;
        } else if (line.startsWith("new file mode")) {
            b.added = true;
        } else if (line.startsWith("deleted file mode")) {
            b.deleted = true;
        } else if (line.startsWith("Binary files ") || line.equals("GIT binary patch")) {
            b.binary = true;
        } else {
            return line.startsWith("index ") || line.startsWith("old mode") || line.startsWith("new mode")
                    || line.startsWith("similarity index") || line.startsWith("dissimilarity index");
        }
        return true;
    }

    private static int count(String group) {
        return group == null ? 1 : Integer.parseInt(group);
    }

    private static String stripPrefix(String path, String prefix) {
        if (DEV_NULL.equals(path)) {
            return DEV_NULL;
        }
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    /** Decodes git's C-style quoting ({@code "a\tb\303\251"}); unquoted input is returned as is. */
    static String unquote(String s) {
        int tab = s.indexOf('\t');
        if (!s.startsWith("\"") && tab >= 0) {
            s = s.substring(0, tab);
        }
        if (s.length() < 2 || !s.startsWith("\"") || !s.endsWith("\"")) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String body = s.substring(1, s.length() - 1);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\' || i + 1 >= body.length()) {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(bytes, 0, bytes.length);
                continue;
            }
            char n = body.charAt(++i);
            switch (n) {
                case 'n' -> out.write('\n');
                case 't' -> out.write('\t');
                case 'r' -> out.write('\r');
                case 'a' -> out.write(7);
                case 'b' -> out.write('\b');
                case 'f' -> out.write('\f');
                case 'v' -> out.write(11);
                case '"', '\\' -> out.write(n);
                default -> {
                    if (n >= '0' && n <= '7' && i + 2 < body.length()) {
                        out.write(Integer.parseInt(body.substring(i, i + 3), 8));
                        i += 2;
                    } else {
                        out.write('\\');
                        out.write(n);
                    }
                }
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Recovers {@code a/X b/X} paths from the {@code diff --git} line when no other header names them. */
    static String[] pathsFromGitHeader(String header) {
        if (header.startsWith("\"")) {
            int end = header.indexOf("\" ", 1);
            if (end > 0) {
                return new String[]{stripPrefix(unquote(header.substring(0, end + 1)), "a/"),
                        stripPrefix(unquote(header.substring(end + 2)), "b/")};
            }
        }
        int half = (header.length() - 1) / 2;
        if (header.length() % 2 == 1 && header.charAt(half) == ' '
                && header.substring(2, half).equals(header.substring(half + 3))) {
            String p = header.substring(2, half);
            return new String[]{p, p};
        }
        int split = header.lastIndexOf(" b/");
        if (split > 0) {
            return new String[]{stripPrefix(header.substring(0, split), "a/"), header.substring(split + 3)};
        }
        return new String[]{header, header};
    }

    private static final class Hunk {
        final int oldCount;
        final int newCount;
        final int oldStart;
        final int newStart;
        int nextOld;
        int nextNew;
        final List<FileDiff.Line> lines = new ArrayList<>();

        Hunk(int oldStart, int oldCount, int newStart, int newCount) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
            this.nextOld = oldStart;
            this.nextNew = newStart;
        }

        boolean exhausted() {
            return nextOld >= oldStart + oldCount && nextNew >= newStart + newCount;
        }

        FileDiff.Hunk toRecord() {
            return new FileDiff.Hunk(oldStart, oldCount, newStart, newCount, List.copyOf(lines));
        }
    }

    private static final class Builder {
        final String gitHeader;
        String oldPath;
        String newPath;
        boolean added;
        boolean deleted;
        boolean renamed;
        boolean copied;
        boolean binary;
        int additions;
        int deletions;
        final List<Hunk> hunks = new ArrayList<>();
        final StringBuilder text = new StringBuilder();

        Builder(String gitHeader) {
            this.gitHeader = gitHeader;
        }

        FileDiff build() {
            if (oldPath == null || newPath == null) {
                String[] fromHeader = pathsFromGitHeader(gitHeader);
                if (oldPath == null) {
                    oldPath = fromHeader[0];
                }
                if (newPath == null) {
                    newPath = fromHeader[1];
                }
            }
            if (DEV_NULL.equals(oldPath)) {
                added = true;
                oldPath = newPath;
            }
            if (DEV_NULL.equals(newPath)) {
                deleted = true;
                newPath = oldPath;
            }
            FileDiff.ChangeType type = added ? FileDiff.ChangeType.ADDED
                    : deleted ? FileDiff.ChangeType.DELETED
                    : renamed ? FileDiff.ChangeType.RENAMED
                    : copied ? FileDiff.ChangeType.COPIED
                    : FileDiff.ChangeType.MODIFIED;
            String hunksText = text.isEmpty() ? "" : text.substring(0, text.length() - 1);
            return new FileDiff(oldPath, newPath, type, binary, hunks.stream().map(Hunk::toRecord).toList(),
                    hunksText, additions, deletions);
        }
    }
}

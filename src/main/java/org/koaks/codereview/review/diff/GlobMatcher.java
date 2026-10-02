package org.koaks.codereview.review.diff;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Doublestar-style glob over repository-relative paths. Unlike {@code java.nio} globs, every
 * {@code **}{@code /} may also match zero directories, so {@code **}{@code /*.java} matches
 * {@code Foo.java} at the root.
 */
public final class GlobMatcher {

    private final List<PathMatcher> matchers;
    private final boolean ignoreCase;

    private GlobMatcher(List<PathMatcher> matchers, boolean ignoreCase) {
        this.matchers = matchers;
        this.ignoreCase = ignoreCase;
    }

    public static GlobMatcher of(List<String> patterns, boolean ignoreCase) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            String p = ignoreCase ? pattern.toLowerCase(Locale.ROOT) : pattern;
            for (String variant : zeroDirVariants(p)) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + variant));
            }
        }
        return new GlobMatcher(matchers, ignoreCase);
    }

    public boolean matches(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Path p = Path.of(ignoreCase ? path.toLowerCase(Locale.ROOT) : path);
        for (PathMatcher m : matchers) {
            if (m.matches(p)) {
                return true;
            }
        }
        return false;
    }

    /** Every combination of keeping or dropping each {@code **}{@code /} segment. */
    static Set<String> zeroDirVariants(String pattern) {
        Set<String> out = new LinkedHashSet<>();
        collectVariants(pattern, 0, out);
        return out;
    }

    private static void collectVariants(String pattern, int from, Set<String> out) {
        int at = pattern.indexOf("**/", from);
        if (at < 0) {
            out.add(pattern);
            return;
        }
        collectVariants(pattern, at + 3, out);
        collectVariants(pattern.substring(0, at) + pattern.substring(at + 3), at, out);
    }
}

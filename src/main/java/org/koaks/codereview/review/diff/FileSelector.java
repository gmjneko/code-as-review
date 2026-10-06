package org.koaks.codereview.review.diff;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which changed files are reviewed. Deleted files are not reviewed but stay in the change
 * list shown to the model; credential paths never reach the model at all.
 */
public final class FileSelector {

    public enum Reason {
        BINARY,
        SECRET,
        EXTENSION,
        DEFAULT_PATH,
        DELETED,
        TOO_LARGE
    }

    /**
     * @param reviewable files to review
     * @param context    every file the prompts may list as part of the change (reviewable + deleted)
     * @param excluded   path to the reason it is not reviewed
     */
    public record Selection(List<FileDiff> reviewable, List<FileDiff> context, Map<String, Reason> excluded) {
    }

    private final GlobMatcher secretPaths;
    private final GlobMatcher excludedPaths;
    private final Set<String> allowedExtensions;
    private final int maxFileDiffTokens;

    public FileSelector(List<String> secretPatterns, List<String> excludePatterns, Set<String> allowedExtensions,
                        int maxFileDiffTokens) {
        this.secretPaths = GlobMatcher.of(secretPatterns, true);
        this.excludedPaths = GlobMatcher.of(excludePatterns, false);
        this.allowedExtensions = allowedExtensions;
        this.maxFileDiffTokens = maxFileDiffTokens;
    }

    public static FileSelector fromClasspath(int maxFileDiffTokens) {
        JsonMapper json = JsonMapper.builder().build();
        TypeReference<List<String>> listOfString = new TypeReference<>() {
        };
        List<String> secrets = json.readValue(resource("review/secret-patterns.json"), listOfString);
        List<String> excludes = json.readValue(resource("review/exclude-patterns.json"), listOfString);
        Set<String> exts = new HashSet<>();
        json.readValue(resource("review/supported-extensions.json"), listOfString)
                .forEach(e -> exts.add(e.toLowerCase(Locale.ROOT)));
        return new FileSelector(secrets, excludes, exts, maxFileDiffTokens);
    }

    public Selection select(List<FileDiff> diffs) {
        List<FileDiff> reviewable = new ArrayList<>();
        List<FileDiff> context = new ArrayList<>();
        Map<String, Reason> excluded = new LinkedHashMap<>();
        for (FileDiff d : diffs) {
            Reason reason = whyExcluded(d);
            if (reason == null) {
                reviewable.add(d);
                context.add(d);
            } else {
                excluded.put(d.path(), reason);
                if (reason == Reason.DELETED) {
                    context.add(d);
                }
            }
        }
        return new Selection(reviewable, context, excluded);
    }

    private Reason whyExcluded(FileDiff d) {
        if (d.binary()) {
            return Reason.BINARY;
        }
        if (secretPaths.matches(d.oldPath()) || secretPaths.matches(d.newPath())) {
            return Reason.SECRET;
        }
        String path = d.path();
        String ext = extension(path);
        if (!ext.isEmpty() && !allowedExtensions.contains(ext)) {
            return Reason.EXTENSION;
        }
        if (excludedPaths.matches(path)) {
            return Reason.DEFAULT_PATH;
        }
        if (d.changeType() == FileDiff.ChangeType.DELETED) {
            return Reason.DELETED;
        }
        if (d.hunks().isEmpty()) {
            return Reason.DEFAULT_PATH;
        }
        if (maxFileDiffTokens > 0 && TokenEstimator.estimate(d.hunksText()) > maxFileDiffTokens) {
            return Reason.TOO_LARGE;
        }
        return null;
    }

    static String extension(String path) {
        int slash = path.lastIndexOf('/');
        String name = path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static InputStream resource(String name) {
        InputStream in = FileSelector.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new UncheckedIOException(new IOException("missing classpath resource " + name));
        }
        return in;
    }

}

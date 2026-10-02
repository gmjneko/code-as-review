package org.koaks.codereview.scm.local;

import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.config.CodeReviewProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Local repositories are directories on the server, so registration is limited to configured
 * roots. Paths are compared after resolving symlinks.
 */
@Component
public class LocalPathPolicy {

    private final List<Path> roots;

    @Autowired
    public LocalPathPolicy(CodeReviewProperties properties) {
        this(properties.localRepoRoots());
    }

    LocalPathPolicy(List<Path> roots) {
        this.roots = roots == null ? List.of() : roots.stream().map(LocalPathPolicy::canonical).toList();
    }

    public Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw BizException.badRequest("localPath is required");
        }
        Path path = Path.of(rawPath);
        if (!path.isAbsolute()) {
            throw BizException.badRequest("localPath must be absolute");
        }
        if (!Files.isDirectory(path)) {
            throw BizException.badRequest("localPath is not a directory");
        }
        Path real = canonical(path);
        if (roots.stream().noneMatch(real::startsWith)) {
            throw BizException.badRequest("localPath is outside the allowed repository roots");
        }
        return real;
    }

    private static Path canonical(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException("cannot resolve " + path, e);
        }
    }
}

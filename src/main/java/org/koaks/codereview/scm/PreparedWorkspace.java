package org.koaks.codereview.scm;

import java.nio.file.Path;

/**
 * A checkout the agent may read plus the unified diff under review. Closing it releases any
 * temporary checkout the provider created.
 *
 * @param codeRoot directory whose contents reflect the reviewed (new) revision
 * @param baseSha  revision the diff is computed against
 * @param headSha  reviewed revision; {@code null} for uncommitted changes
 * @param diff     unified diff text
 */
public record PreparedWorkspace(Path codeRoot, String baseSha, String headSha, String diff, Runnable cleanup)
        implements AutoCloseable {

    @Override
    public void close() {
        if (cleanup != null) {
            cleanup.run();
        }
    }
}

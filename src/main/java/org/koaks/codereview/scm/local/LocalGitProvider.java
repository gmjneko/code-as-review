package org.koaks.codereview.scm.local;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.common.lock.KeyedLock;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.scm.PreparedWorkspace;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.ScmProvider;
import org.koaks.codereview.scm.git.GitCli;
import org.koaks.codereview.scm.git.GitException;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Reviews a git checkout that already exists on the server. Commit ranges are materialised in a
 * detached {@code git worktree} under the task directory, so the user's checkout is never switched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalGitProvider implements ScmProvider {

    /** git's well-known empty tree, used as the base when a repository has no commits yet. */
    static final String EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
    private static final int MAX_UNTRACKED_FILES = 500;
    private static final String[] DIFF_FLAGS = {"--no-color", "--no-ext-diff", "-M"};

    private final GitCli git;
    private final LocalPathPolicy pathPolicy;
    private final KeyedLock lock;

    @Override
    public SourceType sourceType() {
        return SourceType.LOCAL;
    }

    @Override
    public void validate(CodeRepository repository) {
        Path dir = pathPolicy.resolve(repository.getLocalPath());
        GitCli.Result top = git.run(dir, "rev-parse", "--show-toplevel");
        if (!top.ok()) {
            throw BizException.badRequest("localPath is not a git repository");
        }
        Path topLevel = pathPolicy.resolve(top.stdout().strip());
        repository.setLocalPath(topLevel.toString());
    }

    @Override
    public boolean supports(ReviewTarget target) {
        return target instanceof ReviewTarget.LocalWorkingTree || target instanceof ReviewTarget.CommitRange;
    }

    @Override
    public PreparedWorkspace prepare(CodeRepository repository, ReviewTarget target, Path taskDir) {
        Path repoDir = pathPolicy.resolve(repository.getLocalPath());
        return switch (target) {
            case ReviewTarget.LocalWorkingTree ignored -> prepareWorkingTree(repoDir);
            case ReviewTarget.CommitRange range -> prepareCommitRange(repository.getId(), repoDir, range, taskDir);
            default -> throw BizException.badRequest("local repositories do not support " + target);
        };
    }

    private PreparedWorkspace prepareWorkingTree(Path repoDir) {
        GitCli.Result head = git.run(repoDir, "rev-parse", "--verify", "-q", "HEAD^{commit}");
        String base = head.ok() ? head.stdout().strip() : EMPTY_TREE;
        StringBuilder diff = new StringBuilder(git.runChecked(repoDir, concat("diff", DIFF_FLAGS, base)));

        String untracked = git.runChecked(repoDir, "ls-files", "-z", "--others", "--exclude-standard");
        int count = 0;
        for (String file : untracked.split("\0")) {
            if (file.isEmpty()) {
                continue;
            }
            if (++count > MAX_UNTRACKED_FILES) {
                log.warn("More than {} untracked files in {}; the rest are not reviewed", MAX_UNTRACKED_FILES, repoDir);
                break;
            }
            GitCli.Result r = git.run(repoDir, "diff", "--no-color", "--no-ext-diff", "--no-index", "--", "/dev/null", file);
            // --no-index exits with 1 when the files differ, which is always the case here.
            if (r.exitCode() > 1) {
                throw new GitException("git diff --no-index failed for " + file + ": " + r.stderr().strip());
            }
            appendWithNewline(diff, r.stdout());
        }
        return new PreparedWorkspace(repoDir, base, null, diff.toString(), null);
    }

    private PreparedWorkspace prepareCommitRange(Long repoId, Path repoDir, ReviewTarget.CommitRange range, Path taskDir) {
        String baseSha = resolveCommit(repoDir, range.base());
        String headSha = resolveCommit(repoDir, range.head());
        GitCli.Result mb = git.run(repoDir, "merge-base", baseSha, headSha);
        String diffBase = mb.ok() ? mb.stdout().strip() : baseSha;

        Path worktree = taskDir.resolve("worktree").toAbsolutePath();
        String lockKey = "cr:lock:repo:" + repoId;
        lock.withLock(lockKey, () -> {
            git.runChecked(repoDir, "worktree", "add", "--detach", worktree.toString(), headSha);
        });
        try {
            String diff = git.runChecked(repoDir, concat("diff", DIFF_FLAGS, diffBase, headSha));
            return new PreparedWorkspace(worktree, diffBase, headSha, diff,
                    () -> removeWorktree(lockKey, repoDir, worktree));
        } catch (RuntimeException e) {
            removeWorktree(lockKey, repoDir, worktree);
            throw e;
        }
    }

    private String resolveCommit(Path repoDir, String ref) {
        String safe;
        try {
            safe = GitCli.requireSafeRef(ref);
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest(e.getMessage());
        }
        GitCli.Result r = git.run(repoDir, "rev-parse", "--verify", "-q", "--end-of-options", safe + "^{commit}");
        if (!r.ok()) {
            throw BizException.badRequest("unknown revision: " + ref);
        }
        return r.stdout().strip();
    }

    private void removeWorktree(String lockKey, Path repoDir, Path worktree) {
        try {
            lock.withLock(lockKey, () -> {
                git.run(repoDir, "worktree", "remove", "--force", worktree.toString());
                git.run(repoDir, "worktree", "prune");
            });
        } catch (RuntimeException e) {
            log.warn("Failed to remove worktree {}: {}", worktree, e.getMessage());
        }
    }

    private static void appendWithNewline(StringBuilder sb, String text) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(text);
    }

    private static String[] concat(String first, String[] middle, String... rest) {
        String[] out = new String[1 + middle.length + rest.length];
        out[0] = first;
        System.arraycopy(middle, 0, out, 1, middle.length);
        System.arraycopy(rest, 0, out, 1 + middle.length, rest.length);
        return out;
    }
}

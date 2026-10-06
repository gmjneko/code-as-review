package org.koaks.codereview.scm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.common.lock.KeyedLock;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.review.diff.DiffParser;
import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.scm.PreparedWorkspace;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.git.GitCli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalGitProviderTest {

    @TempDir
    Path tmp;

    private Path repoDir;
    private GitCli git;
    private LocalGitProvider provider;
    private CodeRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        Path root = tmp.toRealPath();
        repoDir = Files.createDirectories(root.resolve("repo"));
        CodeReviewProperties props = new CodeReviewProperties(root, List.of(root), null,
                new CodeReviewProperties.Review("medium", 0, 0, 0, 0, 0, 0, 30), null, null);
        git = new GitCli(props);
        KeyedLock lock = new KeyedLock() {
            @Override
            public synchronized <T> void withLock(String key, Supplier<T> action) {
                action.get();
            }
        };
        provider = new LocalGitProvider(git, new LocalPathPolicy(List.of(root)), lock);

        git.runChecked(repoDir, "init", "-q", "-b", "main");
        git.runChecked(repoDir, "config", "user.email", "t@example.com");
        git.runChecked(repoDir, "config", "user.name", "t");
        Files.createDirectories(repoDir.resolve("src"));
        Files.writeString(repoDir.resolve("src/A.java"), "class A {}\n");
        git.runChecked(repoDir, "add", ".");
        git.runChecked(repoDir, "commit", "-q", "-m", "init");
        git.runChecked(repoDir, "checkout", "-q", "-b", "feature");
        Files.writeString(repoDir.resolve("src/B.java"), "class B {}\n");
        git.runChecked(repoDir, "add", ".");
        git.runChecked(repoDir, "commit", "-q", "-m", "add B");
        git.runChecked(repoDir, "checkout", "-q", "main");

        repo = new CodeRepository();
        repo.setId(1L);
        repo.setSourceType(SourceType.LOCAL);
        repo.setLocalPath(repoDir.resolve("src").toString());
    }

    @Test
    void validateNormalisesToTopLevelAndRejectsOutsidePaths() {
        provider.validate(repo);
        assertThat(repo.getLocalPath()).isEqualTo(repoDir.toString());

        CodeRepository outside = new CodeRepository();
        outside.setLocalPath(System.getProperty("java.io.tmpdir"));
        assertThatThrownBy(() -> provider.validate(outside)).isInstanceOf(BizException.class);
    }

    @Test
    void workingTreeIncludesUnstagedAndUntrackedFiles() throws Exception {
        provider.validate(repo);
        Files.writeString(repoDir.resolve("src/A.java"), "class A { int x; }\n");
        Files.writeString(repoDir.resolve("src/New file.java"), "class N {}\n");

        try (PreparedWorkspace ws = provider.prepare(repo, new ReviewTarget.LocalWorkingTree(), tmp.resolve("t1"))) {
            assertThat(ws.codeRoot()).isEqualTo(repoDir);
            assertThat(ws.headSha()).isNull();
            List<FileDiff> diffs = DiffParser.parse(ws.diff());
            assertThat(diffs).extracting(FileDiff::path).containsExactlyInAnyOrder("src/A.java", "src/New file.java");
            assertThat(diffs).filteredOn(d -> d.path().equals("src/New file.java"))
                    .extracting(FileDiff::changeType).containsExactly(FileDiff.ChangeType.ADDED);
        }
    }

    @Test
    void commitRangeChecksOutHeadInATemporaryWorktree() throws Exception {
        provider.validate(repo);
        Path taskDir = Files.createDirectories(tmp.resolve("t2"));
        Path worktree;
        try (PreparedWorkspace ws = provider.prepare(repo, new ReviewTarget.CommitRange("main", "feature"), taskDir)) {
            worktree = ws.codeRoot();
            assertThat(worktree).startsWith(taskDir);
            assertThat(worktree.resolve("src/B.java")).exists();
            assertThat(repoDir.resolve("src/B.java")).doesNotExist();
            assertThat(DiffParser.parse(ws.diff())).extracting(FileDiff::path).containsExactly("src/B.java");
        }
        assertThat(worktree).doesNotExist();
        assertThat(git.runChecked(repoDir, "worktree", "list")).doesNotContain(worktree.toString());
    }

    @Test
    void rejectsUnknownOrUnsafeRefs() {
        provider.validate(repo);
        assertThatThrownBy(() -> provider.prepare(repo, new ReviewTarget.CommitRange("main", "nope"), tmp))
                .isInstanceOf(BizException.class).hasMessageContaining("unknown revision");
        assertThatThrownBy(() -> provider.prepare(repo, new ReviewTarget.CommitRange("--output=/tmp/x", "main"), tmp))
                .isInstanceOf(BizException.class).hasMessageContaining("invalid git ref");
        assertThat(provider.supports(new ReviewTarget.PullRequest("1"))).isFalse();
    }
}

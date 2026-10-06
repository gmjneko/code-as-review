package org.koaks.codereview.scm.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.common.lock.KeyedLock;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.scm.PreparedWorkspace;
import org.koaks.codereview.scm.ReviewTarget;
import org.koaks.codereview.scm.ScmProvider;
import org.koaks.codereview.scm.credential.service.ScmCredentialService;
import org.koaks.codereview.scm.git.GitCli;
import org.koaks.codereview.scm.git.GitException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubScmProvider implements ScmProvider {

    private final GitCli git;
    private final GitHubClient github;
    private final ScmCredentialService credentials;
    private final KeyedLock lock;
    private final CodeReviewProperties properties;

    @Override
    public SourceType sourceType() {
        return SourceType.GITHUB;
    }

    @Override
    public void validate(CodeRepository repository) {
        if (repository.getCredentialId() == null) {
            throw BizException.badRequest("GitHub repositories require a PAT credential");
        }
        GitHubRepositoryRef ref = GitHubRepositoryRef.parse(repository.getRemoteUrl());
        var credential = credentials.getOwned(repository.getUserId(), repository.getCredentialId());
        if (!"GITHUB".equalsIgnoreCase(credential.getProvider()) || !"PAT".equalsIgnoreCase(credential.getAuthType())) {
            throw BizException.badRequest("credential must be a GitHub PAT");
        }
        if (credential.getHost() != null && !"github.com".equalsIgnoreCase(credential.getHost())) {
            throw BizException.badRequest("only github.com credentials are supported");
        }
        GitHubClient.GitHubRepositoryInfo remote;
        try {
            remote = github.repository(ref.fullName(), credential.getId(), repository.getUserId());
        } catch (RestClientResponseException e) {
            throw BizException.badRequest("GitHub repository could not be accessed: HTTP " + e.getStatusCode().value());
        }
        repository.setExternalFullName(remote.fullName());
        repository.setRemoteUrl(remote.cloneUrl() == null ? ref.cloneUrl() : remote.cloneUrl());
        if (repository.getDefaultBranch() == null || repository.getDefaultBranch().isBlank()) {
            repository.setDefaultBranch(remote.defaultBranch());
        }
    }

    @Override
    public boolean supports(ReviewTarget target) {
        return target instanceof ReviewTarget.PullRequest;
    }

    @Override
    public PreparedWorkspace prepare(CodeRepository repository, ReviewTarget target, Path taskDir) {
        if (!(target instanceof ReviewTarget.PullRequest pullRequest)) {
            throw BizException.badRequest("GitHub currently supports pull request reviews only");
        }
        if (repository.getId() == null || repository.getCredentialId() == null) {
            throw BizException.badRequest("GitHub repository is missing identity or credential");
        }
        GitHubClient.GitHubPullRequest pr = github.pullRequest(repository.getExternalFullName(), pullRequest.number(),
                repository.getCredentialId(), repository.getUserId());
        Path mirror = properties.workspaceRoot().resolve("repositories").resolve(Long.toString(repository.getId()))
                .resolve("mirror.git").toAbsolutePath();
        Path askpass;
        try {
            Files.createDirectories(taskDir);
            askpass = writeAskpass(taskDir, github.token(repository.getCredentialId(), repository.getUserId()));
            Map<String, String> env = Map.of("GIT_ASKPASS", askpass.toString(), "GIT_USERNAME", "x-access-token");
            syncMirror(repository, mirror, env);
            Path worktree = taskDir.resolve("worktree").toAbsolutePath();
            String headSha = GitCli.requireSafeRef(pr.head().sha());
            String baseSha = GitCli.requireSafeRef(pr.base().sha());
            String lockKey = "github:repo:" + repository.getId();
            lock.withLock(lockKey, () -> git.runChecked(mirror, env, "worktree", "add", "--detach",
                    worktree.toString(), headSha));
            GitCli.Result mergeBaseResult = git.run(mirror, env, "merge-base", baseSha, headSha);
            String mergeBase = mergeBaseResult.ok() ? mergeBaseResult.stdout().strip() : baseSha;
            String diff = git.runChecked(mirror, env, "diff", "--no-color", "--no-ext-diff", "-M", mergeBase, headSha);
            return new PreparedWorkspace(worktree, mergeBase, headSha, diff,
                    () -> removeWorktree(lockKey, mirror, worktree));
        } catch (IOException e) {
            deleteQuietly(taskDir);
            throw new GitException("cannot create GitHub task directory", e);
        } catch (RuntimeException e) {
            deleteQuietly(taskDir);
            throw e;
        }
    }

    private void syncMirror(CodeRepository repository, Path mirror, Map<String, String> env) {
        Path parent = mirror.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new GitException("cannot create GitHub mirror directory: " + e.getMessage(), e);
        }
        String remote = repository.getRemoteUrl();
        String lockKey = "github:repo:" + repository.getId();
        lock.withLock(lockKey, () -> {
            if (!Files.isDirectory(mirror)) {
                git.runChecked(parent, env, "clone", "--mirror", remote, mirror.toString());
            }
            git.runChecked(mirror, env, "remote", "set-url", "origin", remote);
            git.runChecked(mirror, env, "fetch", "--prune", "origin",
                    "+refs/heads/*:refs/remotes/origin/*", "+refs/pull/*/head:refs/remotes/origin/pr/*");
        });
    }

    private void removeWorktree(String lockKey, Path mirror, Path worktree) {
        try {
            lock.withLock(lockKey, () -> {
                git.run(mirror, "worktree", "remove", "--force", worktree.toString());
                git.run(mirror, "worktree", "prune");
            });
        } catch (RuntimeException e) {
            log.warn("Failed to remove GitHub worktree {}: {}", worktree, e.getMessage());
        }
    }

    private static Path writeAskpass(Path taskDir, String token) {
        try {
            Path script = taskDir.resolve("git-askpass.sh");
            String escaped = token.replace("'", "'\\''");
            Files.writeString(script, "#!/bin/sh\ncase \"$1\" in\n  *Username*) printf '%s\\n' 'x-access-token' ;;\n  *) printf '%s\\n' '" + escaped + "' ;;\nesac\n");
            try {
                Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                script.toFile().setExecutable(true, true);
            }
            return script;
        } catch (IOException e) {
            throw new GitException("cannot create temporary Git credential helper", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        } catch (IOException ignored) {
        }
    }
}

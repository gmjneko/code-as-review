package org.koaks.codereview.scm.git;

import org.koaks.codereview.config.CodeReviewProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Runs the {@code git} binary non-interactively with a timeout and a bounded output buffer. */
@Component
public class GitCli {

    private static final int MAX_OUTPUT_BYTES = 64 * 1024 * 1024;
    private static final Pattern SAFE_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/@{}^~-]{0,254}$");

    private final Duration timeout;

    @Autowired
    public GitCli(CodeReviewProperties properties) {
        this(Duration.ofSeconds(properties.review().gitTimeoutSeconds()));
    }

    GitCli(Duration timeout) {
        this.timeout = timeout;
    }

    public record Result(int exitCode, String stdout, String stderr) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    public Result run(Path workDir, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-c");
        command.add("core.quotepath=false");
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command).directory(workDir.toFile());
        Map<String, String> env = pb.environment();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("LC_ALL", "C");
        env.put("GIT_OPTIONAL_LOCKS", "0");
        Process process;
        try {
            process = pb.start();
            process.getOutputStream().close();
        } catch (IOException e) {
            throw new GitException("failed to start git: " + e.getMessage(), e);
        }
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> drain(process.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> drain(process.getErrorStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new GitException("git " + String.join(" ", args) + " timed out after " + timeout.toSeconds() + "s");
            }
            return new Result(process.exitValue(), out.get(), err.get());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new GitException("interrupted while running git", e);
        } catch (ExecutionException e) {
            throw new GitException("failed to read git output: " + e.getCause().getMessage(), e.getCause());
        }
    }

    public String runChecked(Path workDir, String... args) {
        Result result = run(workDir, args);
        if (!result.ok()) {
            throw new GitException("git " + args[0] + " failed: " + result.stderr().strip());
        }
        return result.stdout();
    }

    public static String requireSafeRef(String ref) {
        if (ref == null || !SAFE_REF.matcher(ref).matches() || ref.contains("..")) {
            throw new IllegalArgumentException("invalid git ref: " + ref);
        }
        return ref;
    }

    private static String drain(InputStream in) {
        try (in) {
            byte[] bytes = in.readNBytes(MAX_OUTPUT_BYTES);
            if (in.read() != -1) {
                throw new GitException("git output exceeded " + MAX_OUTPUT_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

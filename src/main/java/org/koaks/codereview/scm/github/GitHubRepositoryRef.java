package org.koaks.codereview.scm.github;

import org.koaks.codereview.common.exception.BizException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GitHubRepositoryRef(String fullName) {

    private static final Pattern SCP = Pattern.compile("^git@github\\.com:([^/]+/[^/]+?)(?:\\.git)?$");

    public static GitHubRepositoryRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("GitHub repository URL is required");
        }
        String value = raw.strip();
        Matcher scp = SCP.matcher(value);
        if (scp.matches()) {
            return new GitHubRepositoryRef(normalize(scp.group(1)));
        }
        try {
            URI uri = new URI(value);
            if (!"github.com".equalsIgnoreCase(uri.getHost()) || uri.getPath() == null) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath().replaceFirst("^/", "");
            return new GitHubRepositoryRef(normalize(path));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw BizException.badRequest("remoteUrl must be a GitHub repository URL");
        }
    }

    private static String normalize(String path) {
        String value = path.replaceFirst("/+$", "").replaceFirst("\\.git$", "");
        if (!value.matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) {
            throw BizException.badRequest("remoteUrl must contain a GitHub owner and repository");
        }
        return value;
    }

    public String owner() {
        return fullName.substring(0, fullName.indexOf('/'));
    }

    public String name() {
        return fullName.substring(fullName.indexOf('/') + 1);
    }

    public String cloneUrl() {
        return "https://github.com/" + fullName + ".git";
    }
}

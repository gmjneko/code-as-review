package org.koaks.codereview.scm.github;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubRepositoryRefTest {

    @Test
    void parsesHttpsAndScpUrls() {
        assertThat(GitHubRepositoryRef.parse("https://github.com/acme/demo.git").fullName())
                .isEqualTo("acme/demo");
        assertThat(GitHubRepositoryRef.parse("git@github.com:acme/demo").cloneUrl())
                .isEqualTo("https://github.com/acme/demo.git");
    }

    @Test
    void rejectsNonGithubAndMalformedPaths() {
        assertThatThrownBy(() -> GitHubRepositoryRef.parse("https://gitlab.com/acme/demo"))
                .hasMessageContaining("GitHub");
        assertThatThrownBy(() -> GitHubRepositoryRef.parse("https://github.com/acme"))
                .hasMessageContaining("owner and repository");
    }
}

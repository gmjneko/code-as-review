package org.koaks.codereview.scm.github;

import org.koaks.codereview.scm.credential.service.ScmCredentialService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.List;

@Component
public class GitHubClient {

    private final RestClient client;
    private final ScmCredentialService credentials;

    public GitHubClient(ScmCredentialService credentials) {
        this.client = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.credentials = credentials;
    }

    public GitHubRepositoryInfo repository(String fullName, long credentialId, long userId) {
        return get("/repos/" + fullName, credentialId, userId, GitHubRepositoryInfo.class);
    }

    public GitHubPullRequest pullRequest(String fullName, String number, long credentialId, long userId) {
        return get("/repos/" + fullName + "/pulls/" + number, credentialId, userId, GitHubPullRequest.class);
    }

    public GitHubIssue issue(String fullName, String number, long credentialId, long userId) {
        return get("/repos/" + fullName + "/issues/" + number, credentialId, userId, GitHubIssue.class);
    }

    public List<GitHubIssueComment> issueComments(String fullName, String number, long credentialId, long userId) {
        GitHubIssueComment[] result = client.get().uri("/repos/" + fullName + "/issues/" + number + "/comments?per_page=100")
                .headers(h -> h.setBearerAuth(token(credentialId, userId)))
                .retrieve().body(GitHubIssueComment[].class);
        return result == null ? List.of() : List.of(result);
    }

    public String createReviewComment(String fullName, String number, String body, String commitId,
                                      String path, int line, long credentialId, long userId) {
        return post("/repos/" + fullName + "/pulls/" + number + "/comments", Map.of(
                "body", body,
                "commit_id", commitId,
                "path", path,
                "line", line,
                "side", "RIGHT"), credentialId, userId);
    }

    public String createIssueComment(String fullName, String number, String body, long credentialId, long userId) {
        return post("/repos/" + fullName + "/issues/" + number + "/comments", Map.of("body", body), credentialId, userId);
    }

    public String updateIssueComment(String fullName, long commentId, String body, long credentialId, long userId) {
        GitHubCommentResponse response = client.patch().uri("/repos/" + fullName + "/issues/comments/" + commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token(credentialId, userId)))
                .body(Map.of("body", body)).retrieve().body(GitHubCommentResponse.class);
        return response == null || response.id() == null ? null : Long.toString(response.id());
    }

    public String token(long credentialId, long userId) {
        return credentials.token(credentials.getOwned(userId, credentialId));
    }

    private <T> T get(String path, long credentialId, long userId, Class<T> type) {
        return client.get().uri(path)
                .headers(h -> h.setBearerAuth(token(credentialId, userId)))
                .retrieve().body(type);
    }

    private String post(String path, Object body, long credentialId, long userId) {
        GitHubCommentResponse response = client.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(token(credentialId, userId)))
                .body(body).retrieve().body(GitHubCommentResponse.class);
        return response == null || response.id() == null ? null : Long.toString(response.id());
    }

    public record GitHubRepositoryInfo(@JsonProperty("full_name") String fullName,
                                       @JsonProperty("default_branch") String defaultBranch,
                                       @JsonProperty("clone_url") String cloneUrl) {
    }

    public record GitHubPullRequest(int number, Ref base, Ref head, String title, String body) {
        public record Ref(String ref, String sha) {
        }
    }

    public record GitHubIssue(int number, String title, String body, String state, User user,
                              List<Label> labels, @JsonProperty("html_url") String htmlUrl) {
    }

    public record GitHubIssueComment(Long id, String body, User user,
                                     @JsonProperty("created_at") String createdAt,
                                     @JsonProperty("updated_at") String updatedAt) {
    }

    public record User(String login, String type) {
    }

    public record Label(String name) {
    }

    public record GitHubCommentResponse(Long id) {
    }
}

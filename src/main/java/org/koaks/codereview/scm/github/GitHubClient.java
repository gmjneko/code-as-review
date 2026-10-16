package org.koaks.codereview.scm.github;

import org.koaks.codereview.scm.credential.service.ScmCredentialService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Component
/**
 * GitHub REST API 适配器。
 *
 * <p>RAG 使用这里的原始 Issue、PR 和普通评论接口，而不是只依赖 webhook 中的不完整
 * payload。列表接口统一分页到 100 条，保证全量同步不会只导入第一页。</p>
 */
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

    public List<GitHubIssue> issues(String fullName, String state, long credentialId, long userId) {
        // GitHub 的 /issues 同时返回 PR；全量同步时由 RagTaskRunner 过滤 pull_request 字段。
        return pages("/repos/" + fullName + "/issues?state=" + state + "&per_page=100&page=", credentialId,
                userId, GitHubIssue[].class);
    }

    public List<GitHubPullRequest> pullRequests(String fullName, String state, long credentialId, long userId) {
        return pages("/repos/" + fullName + "/pulls?state=" + state + "&per_page=100&page=", credentialId,
                userId, GitHubPullRequest[].class);
    }

    public List<GitHubIssueComment> issueComments(String fullName, String number, long credentialId, long userId) {
        return pages("/repos/" + fullName + "/issues/" + number + "/comments?per_page=100&page=", credentialId,
                userId, GitHubIssueComment[].class);
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

    private <T> List<T> pages(String pathPrefix, long credentialId, long userId, Class<T[]> type) {
        List<T> result = new ArrayList<>();
        // 1000 页是保护上限，正常情况下通过“返回少于 100 条”结束分页。
        for (int page = 1; page <= 1000; page++) {
            T[] pageItems = client.get().uri(pathPrefix + page)
                    .headers(h -> h.setBearerAuth(token(credentialId, userId)))
                    .retrieve().body(type);
            if (pageItems == null || pageItems.length == 0) break;
            result.addAll(List.of(pageItems));
            if (pageItems.length < 100) break;
        }
        return result;
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

    public record GitHubPullRequest(int number, Ref base, Ref head, String title, String body, String state,
                                    User user, @JsonProperty("html_url") String htmlUrl,
                                    @JsonProperty("updated_at") String updatedAt) {
        public GitHubPullRequest(int number, Ref base, Ref head, String title, String body) {
            this(number, base, head, title, body, "open", null, null, null);
        }

        public record Ref(String ref, String sha) {
        }
    }

    public record GitHubIssue(int number, String title, String body, String state, User user,
                              List<Label> labels, @JsonProperty("html_url") String htmlUrl,
                              @JsonProperty("updated_at") String updatedAt,
                              @JsonProperty("pull_request") PullRequestRef pullRequest) {
        public GitHubIssue(int number, String title, String body, String state, User user,
                           List<Label> labels, String htmlUrl) {
            this(number, title, body, state, user, labels, htmlUrl, null, null);
        }

        public record PullRequestRef(String url) {
        }
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

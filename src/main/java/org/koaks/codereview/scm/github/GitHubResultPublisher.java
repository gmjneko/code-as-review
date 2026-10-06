package org.koaks.codereview.scm.github;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;
import org.koaks.codereview.repo.service.RepoService;
import org.koaks.codereview.review.comment.CandidateComment;
import org.koaks.codereview.review.domain.ReviewComment;
import org.koaks.codereview.review.domain.ReviewEnums;
import org.koaks.codereview.review.domain.ReviewTask;
import org.koaks.codereview.review.mapper.ReviewCommentMapper;
import org.koaks.codereview.review.publish.ResultPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class GitHubResultPublisher implements ResultPublisher {

    private final RepoService repos;
    private final ReviewCommentMapper comments;
    private final GitHubClient github;

    @Override
    public boolean supports(ReviewTask task) {
        if (task.getTargetType() != ReviewEnums.TargetType.PULL_REQUEST) return false;
        CodeRepository repo = repos.getOwned(task.getUserId(), task.getRepositoryId());
        return repo.getSourceType() == SourceType.GITHUB && repo.getCredentialId() != null
                && repo.getExternalFullName() != null;
    }

    @Override
    public void publish(ReviewTask task, List<CandidateComment> findings) {
        CodeRepository repo = repos.getOwned(task.getUserId(), task.getRepositoryId());
        List<ReviewComment> rows = comments.selectList(Wrappers.<ReviewComment>lambdaQuery()
                .eq(ReviewComment::getTaskId, task.getId())
                .eq(ReviewComment::getStatus, ReviewEnums.CommentStatus.CONFIRMED));
        StringBuilder summary = new StringBuilder();
        for (CandidateComment finding : findings) {
            if (finding.getStatus() == ReviewEnums.CommentStatus.FILTERED) continue;
            ReviewComment row = rows.stream().filter(r -> matches(r, finding)).findFirst().orElse(null);
            if (row != null && row.getExternalCommentId() != null) continue;
            String body = format(finding);
            if (finding.getStartLine() != null && task.getHeadSha() != null) {
                String id = github.createReviewComment(repo.getExternalFullName(), task.getExternalRef(), body,
                        task.getHeadSha(), finding.getPath(), finding.getStartLine(), repo.getCredentialId(), task.getUserId());
                if (row != null && id != null) {
                    row.setExternalCommentId(id);
                    comments.updateById(row);
                }
            } else {
                if (!summary.isEmpty()) summary.append("\n\n");
                summary.append(body);
            }
        }
        if (!summary.isEmpty()) {
            String id = github.createIssueComment(repo.getExternalFullName(), task.getExternalRef(), summary.toString(),
                    repo.getCredentialId(), task.getUserId());
            if (id != null) {
                rows.stream().filter(r -> r.getExternalCommentId() == null && !r.getStatus().equals(ReviewEnums.CommentStatus.FILTERED))
                        .forEach(r -> {
                            r.setExternalCommentId(id);
                            comments.updateById(r);
                        });
            }
        }
    }

    private static boolean matches(ReviewComment row, CandidateComment finding) {
        return Objects.equals(row.getFilePath(), finding.getPath())
                && Objects.equals(row.getContent(), finding.getContent())
                && Objects.equals(row.getStartLine(), finding.getStartLine());
    }

    private static String format(CandidateComment finding) {
        StringBuilder body = new StringBuilder("**").append(finding.getSeverity()).append(" ")
                .append(finding.getCategory()).append("**\n\n").append(finding.getContent());
        if (finding.getSuggestionCode() != null && !finding.getSuggestionCode().isBlank()) {
            body.append("\n\n```suggestion\n").append(finding.getSuggestionCode()).append("\n```");
        }
        return body.toString();
    }
}

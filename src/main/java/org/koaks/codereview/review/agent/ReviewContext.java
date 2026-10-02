package org.koaks.codereview.review.agent;

import org.koaks.codereview.review.comment.CommentCollector;
import org.koaks.codereview.review.diff.FileDiff;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-task state handed to review tools through {@code RuntimeContext}.
 *
 * @param diffsByPath every changed file, including ones not under review, keyed by path
 * @param reviewPaths files the agent may comment on
 */
public record ReviewContext(
        long taskId,
        Map<String, FileDiff> diffsByPath,
        Set<String> reviewPaths,
        CommentCollector comments,
        AtomicInteger round) {
}

package org.koaks.codereview.review.agent;

import io.agentscope.core.model.Model;
import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.review.domain.ReviewEnums;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Everything the agent calls of one review task share.
 *
 * @param contextFiles files listed to the model as part of the change (reviewable and deleted)
 * @param scratchRoot  per-task scratch directory for the agent workspace
 */
public record TaskRuntime(
        long taskId,
        long userId,
        Model model,
        TaskBudget budget,
        CancellationToken cancellation,
        Map<String, FileDiff> diffsByPath,
        List<FileDiff> contextFiles,
        String background,
        ReviewEnums.Effort effort,
        Path codeRoot,
        Path scratchRoot
) {
}

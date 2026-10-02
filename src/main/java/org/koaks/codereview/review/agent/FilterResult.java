package org.koaks.codereview.review.agent;

import java.util.List;

/**
 * Structured output of the filter phase. Component order is load-bearing: the generated schema
 * keeps it, so the model writes its per-comment analysis before it commits to any id.
 */
public record FilterResult(List<String> analysis, List<String> removeIds) {
}

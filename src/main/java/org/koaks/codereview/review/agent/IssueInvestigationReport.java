package org.koaks.codereview.review.agent;

import java.util.List;

/** Structured result returned by the Issue investigation Agent. */
public record IssueInvestigationReport(
        String reproductionStatus,
        String summary,
        List<String> reproductionSteps,
        List<String> observations,
        String rootCause,
        String suggestedFix) {

    public IssueInvestigationReport {
        reproductionSteps = reproductionSteps == null ? List.of() : List.copyOf(reproductionSteps);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public static IssueInvestigationReport fallback(String text) {
        return new IssueInvestigationReport("UNKNOWN", text == null ? "" : text.strip(),
                List.of(), List.of(), "", "");
    }
}

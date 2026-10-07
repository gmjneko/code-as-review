<issue>
Title: {{issue_title}}
State: {{issue_state}}
Author: {{issue_author}}
Labels: {{issue_labels}}

Body:
{{issue_body}}

Comments:
{{issue_comments}}
</issue>

<repository>
Baseline ref: {{base_ref}}
Baseline SHA: {{base_sha}}
</repository>

<background>
{{requirement_background}}
</background>

Investigate this Issue in the workspace. Read the relevant code, identify a likely execution path,
and run the smallest useful reproduction or test commands. Then return a structured
IssueInvestigationReport with exactly these fields:
- reproductionStatus
- summary
- reproductionSteps
- observations
- rootCause
- suggestedFix

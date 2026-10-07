package org.koaks.codereview.webhook.domain;

public final class WebhookEnums {

    private WebhookEnums() {
    }

    public enum EventKind {
        PULL_REQUEST,
        ISSUE,
        PR_COMMENT,
        ISSUE_COMMENT
    }

    public enum Mode {
        AUTO,
        COMMAND
    }

    public enum EventStatus {
        RECEIVED,
        IGNORED,
        DISPATCHED,
        FAILED
    }

    public enum IssueTaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }
    }
}

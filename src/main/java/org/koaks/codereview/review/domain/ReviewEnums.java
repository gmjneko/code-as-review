package org.koaks.codereview.review.domain;

public final class ReviewEnums {

    private ReviewEnums() {
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }
    }

    public enum TargetType {
        LOCAL_WORKING_TREE,
        COMMIT_RANGE,
        PULL_REQUEST,
        ISSUE
    }

    public enum TriggerType {
        API,
        WEBHOOK_COMMAND
    }

    public enum CommentStatus {
        CONFIRMED,
        FILTERED
    }

    /** Review depth: rounds of the main review loop. */
    public enum Effort {
        LOW(1),
        MEDIUM(2),
        HIGH(3);

        private final int rounds;

        Effort(int rounds) {
            this.rounds = rounds;
        }

        public int rounds() {
            return rounds;
        }
    }
}

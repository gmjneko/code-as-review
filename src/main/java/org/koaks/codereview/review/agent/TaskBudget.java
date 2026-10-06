package org.koaks.codereview.review.agent;

import java.util.concurrent.atomic.AtomicLong;

/** Token usage of a whole review task, shared by every agent conversation in it. */
public class TaskBudget {

    private final long maxTokens;
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    public TaskBudget(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void record(long input, long output) {
        inputTokens.addAndGet(input);
        outputTokens.addAndGet(output);
    }

    public boolean exceeded() {
        return maxTokens > 0 && total() >= maxTokens;
    }

    public long total() {
        return inputTokens.get() + outputTokens.get();
    }

    public long inputTokens() {
        return inputTokens.get();
    }

    public long outputTokens() {
        return outputTokens.get();
    }

    public long maxTokens() {
        return maxTokens;
    }

}

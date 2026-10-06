package org.koaks.codereview.review.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/** Accounts every model call against the task budget and refuses new calls once it is spent. */
public class BudgetMiddleware implements MiddlewareBase {

    private final TaskBudget budget;

    public BudgetMiddleware(TaskBudget budget) {
        this.budget = budget;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        if (budget.exceeded()) {
            return Flux.error(new BudgetExceededException(budget.total(), budget.maxTokens()));
        }
        return next.apply(input).doOnNext(event -> {
            if (event instanceof ModelCallEndEvent end && end.getUsage() != null) {
                ChatUsage usage = end.getUsage();
                budget.record(usage.getInputTokens(), usage.getOutputTokens());
            }
        });
    }

    public static class BudgetExceededException extends RuntimeException {

        public BudgetExceededException(long used, long max) {
            super("token budget exhausted: used " + used + " of " + max);
        }
    }

}

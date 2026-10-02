package org.koaks.codereview.review.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation for one review task: a flag plus interrupt hooks of in-flight agent calls. */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> hooks = new CopyOnWriteArrayList<>();

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            hooks.forEach(Runnable::run);
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new CancelledException();
        }
    }

    /** Registers {@code hook} for the duration of the returned handle. */
    public Registration register(Runnable hook) {
        hooks.add(hook);
        if (cancelled.get()) {
            hook.run();
        }
        return () -> hooks.remove(hook);
    }

    public interface Registration extends AutoCloseable {

        @Override
        void close();
    }

    public static class CancelledException extends RuntimeException {

        public CancelledException() {
            super("review task cancelled");
        }
    }
}

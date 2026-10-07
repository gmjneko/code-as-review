package org.koaks.codereview.common.lock;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock backed by Redisson. The default path uses Redisson's watchdog so a long
 * running Git operation does not lose its lock after a fixed lease expires.
 */
@Component
@RequiredArgsConstructor
public class RedissonLock implements KeyedLock {

    private static final String KEY_PREFIX = "cr:redisson:lock:";
    private static final Duration DEFAULT_WAIT = Duration.ofMinutes(5);

    private final RedissonClient redisson;

    @Override
    public <T> void withLock(String key, Supplier<T> action) {
        withWatchdog(key, DEFAULT_WAIT, action);
    }

    /**
     * Acquires the lock for at most {@code waitAtMost}, using the supplied fixed lease duration.
     * The interface method uses the watchdog-based overload above for automatic renewal.
     */
    public <T> T withLock(String key, Duration lease, Duration waitAtMost, Supplier<T> action) {
        return execute(key, waitAtMost, (lock, waitMillis) ->
                lock.tryLock(waitMillis, lease.toMillis(), TimeUnit.MILLISECONDS), action);
    }

    private <T> T withWatchdog(String key, Duration waitAtMost, Supplier<T> action) {
        return execute(key, waitAtMost, (lock, waitMillis) ->
                lock.tryLock(waitMillis, TimeUnit.MILLISECONDS), action);
    }

    private <T> T execute(String key, Duration waitAtMost, LockAttempt attempt, Supplier<T> action) {
        RLock lock = redisson.getLock(KEY_PREFIX + key);
        boolean acquired = false;
        try {
            acquired = attempt.tryLock(lock, waitAtMost.toMillis());
            if (!acquired) {
                throw new IllegalStateException("timed out waiting for lock " + key);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for lock " + key, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @FunctionalInterface
    private interface LockAttempt {
        boolean tryLock(RLock lock, long waitMillis) throws InterruptedException;
    }

}

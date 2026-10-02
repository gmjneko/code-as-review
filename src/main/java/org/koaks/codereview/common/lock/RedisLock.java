package org.koaks.codereview.common.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Minimal Redis mutex: {@code SET NX PX} to acquire, compare-and-delete to release so a holder
 * whose lease expired cannot release someone else's lock.
 */
@Component
@RequiredArgsConstructor
public class RedisLock implements KeyedLock {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private static final Duration RETRY_INTERVAL = Duration.ofMillis(200);
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(5);
    private static final Duration DEFAULT_WAIT = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    @Override
    public <T> T withLock(String key, Supplier<T> action) {
        return withLock(key, DEFAULT_LEASE, DEFAULT_WAIT, action);
    }

    public <T> T withLock(String key, Duration lease, Duration waitAtMost, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + waitAtMost.toNanos();
        while (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, lease))) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("timed out waiting for lock " + key);
            }
            try {
                Thread.sleep(RETRY_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for lock " + key, e);
            }
        }
        try {
            return action.get();
        } finally {
            redis.execute(RELEASE, List.of(key), token);
        }
    }
}

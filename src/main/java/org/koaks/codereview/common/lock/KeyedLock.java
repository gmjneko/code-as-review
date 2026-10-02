package org.koaks.codereview.common.lock;

import java.util.function.Supplier;

public interface KeyedLock {

    <T> T withLock(String key, Supplier<T> action);

    default void withLock(String key, Runnable action) {
        withLock(key, () -> {
            action.run();
            return null;
        });
    }
}

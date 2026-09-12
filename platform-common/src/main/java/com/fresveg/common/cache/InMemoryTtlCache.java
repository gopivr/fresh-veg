package com.fresveg.common.cache;

import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InMemoryTtlCache implements TtlCache {
    private final Clock clock;
    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    public InMemoryTtlCache(Clock clock) { this.clock = clock; }

    @Override
    public <T> T get(String key, Duration ttl, Supplier<T> loader) {
        Instant now = clock.instant();
        Entry current = values.get(key);
        if (current != null && current.expiresAt().isAfter(now)) {
            @SuppressWarnings("unchecked") T value = (T) current.value();
            return value;
        }
        T loaded = loader.get();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            values.put(key, new Entry(loaded, now.plus(ttl)));
        }
        return loaded;
    }

    @Override
    public void evictByPrefix(String prefix) {
        values.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private record Entry(Object value, Instant expiresAt) { }
}

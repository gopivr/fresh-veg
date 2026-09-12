package com.fresveg.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

public interface TtlCache {
    <T> T get(String key, Duration ttl, Supplier<T> loader);
    void evictByPrefix(String prefix);
}

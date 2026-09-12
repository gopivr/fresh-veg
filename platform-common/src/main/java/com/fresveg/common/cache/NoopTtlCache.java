package com.fresveg.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

public final class NoopTtlCache implements TtlCache {
    @Override public <T> T get(String key, Duration ttl, Supplier<T> loader) { return loader.get(); }
    @Override public void evictByPrefix(String prefix) { }
}

package com.fresveg.common.cache;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Clock fresvegClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnMissingBean
    TtlCache ttlCache(CacheProperties properties, Clock clock) {
        return properties.isEnabled() ? new InMemoryTtlCache(clock) : new NoopTtlCache();
    }
}

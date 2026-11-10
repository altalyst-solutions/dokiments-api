package com.dokiments.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's clock. Code that needs the current time takes a {@link Clock} instead of calling
 * {@code Instant.now()}, so tests can fix the time and check exact values.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

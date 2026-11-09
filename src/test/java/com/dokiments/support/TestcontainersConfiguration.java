package com.dokiments.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * Real infrastructure for integration tests, started once per Spring test context and wired in automatically through
 * {@link ServiceConnection} (no URLs or ports to configure).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Spring starts and stops the container with the test context, so it isn't closed here.
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(TestImages.REDIS).withExposedPorts(6379);
    }
}

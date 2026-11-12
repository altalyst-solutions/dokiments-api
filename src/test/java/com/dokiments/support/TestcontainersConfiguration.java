package com.dokiments.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Real infrastructure for integration tests, started once per Spring test context.
 *
 * <p>Redis is wired in automatically through {@link ServiceConnection}. Spring Boot has no such support for the
 * Firebase emulators, so their addresses are passed to the application as settings once the container is running. Every
 * test context gets fresh, empty emulators, so tests never see local development data.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Spring starts and stops the containers with the test context, so they aren't closed here.
    @Bean
    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(TestImages.REDIS).withExposedPorts(6379);
    }

    @Bean
    FirebaseEmulatorsContainer firebaseEmulatorsContainer() {
        return new FirebaseEmulatorsContainer();
    }

    /** Points the application at the emulators. The project ID comes from {@code application-test.yml}. */
    @Bean
    DynamicPropertyRegistrar firebaseEmulatorsProperties(FirebaseEmulatorsContainer emulators) {
        return registry -> registry.add("dokiments.firestore.emulator-host", emulators::firestoreEndpoint);
    }
}

package com.dokiments.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.gcloud.FirestoreEmulatorContainer;

/**
 * Real infrastructure for integration tests, started once per Spring test context.
 *
 * <p>Redis is wired in automatically through {@link ServiceConnection}. Spring Boot has no such support for the
 * Firestore emulator, so its address is passed to the application as a setting once the container is running. Every
 * test context gets a fresh, empty emulator, so tests never see local development data.
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
    FirestoreEmulatorContainer firestoreEmulatorContainer() {
        return new FirestoreEmulatorContainer(TestImages.FIRESTORE_EMULATOR);
    }

    /** Points the application at the emulator. The project ID comes from {@code application-test.yml}. */
    @Bean
    DynamicPropertyRegistrar firestoreEmulatorProperties(FirestoreEmulatorContainer emulator) {
        return registry -> registry.add("dokiments.firestore.emulator-host", emulator::getEmulatorEndpoint);
    }
}

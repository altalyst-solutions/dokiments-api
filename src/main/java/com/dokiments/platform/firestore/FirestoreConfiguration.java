package com.dokiments.platform.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOpenTelemetryOptions;
import com.google.cloud.firestore.FirestoreOptions;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the application's {@link Firestore} client, for the emulator or the real project depending on
 * {@link FirestoreProperties}.
 *
 * <p>The client is thread-safe and meant to be shared, so there is exactly one. Spring closes it on shutdown.
 */
@Configuration(proxyBeanMethods = false)
class FirestoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FirestoreConfiguration.class);

    @Bean
    Firestore firestore(FirestoreProperties properties, ObjectProvider<OpenTelemetry> openTelemetry) {
        FirestoreOptions options = withTracing(
                properties.usesEmulator() ? emulatorOptions(properties) : cloudOptions(properties),
                openTelemetry.getIfAvailable());
        log.info(
                "Using Firestore project {} {}",
                options.getProjectId(),
                properties.usesEmulator() ? "on the emulator at " + options.getHost() : "in Google Cloud");
        return options.getService();
    }

    /**
     * Records each Firestore call as part of the request's trace, through the application's OpenTelemetry setup, so a
     * slow request shows which reads and writes took the time. Without OpenTelemetry (for example in a test that
     * doesn't start it), the client works as usual.
     *
     * <p>Only tracing: the client can also send its own metrics directly to Google Cloud Monitoring, which stays off,
     * because exports go through the platform's collector.
     */
    private static FirestoreOptions withTracing(FirestoreOptions options, @Nullable OpenTelemetry openTelemetry) {
        if (openTelemetry == null) {
            return options;
        }
        return options.toBuilder()
                .setOpenTelemetryOptions(FirestoreOpenTelemetryOptions.newBuilder()
                        .setOpenTelemetry(openTelemetry)
                        .build())
                .build();
    }

    /**
     * The emulator needs no credentials. The host is set explicitly: without it, the client would connect to
     * {@code firestore.googleapis.com} whatever else is configured, so it is checked once more after building.
     */
    private static FirestoreOptions emulatorOptions(FirestoreProperties properties) {
        String emulatorHost = Objects.requireNonNull(properties.emulatorHost());
        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setProjectId(properties.projectId())
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();
        if (!emulatorHost.equals(options.getHost())) {
            throw new IllegalStateException("The Firestore client was configured for the emulator at " + emulatorHost
                    + " but would connect to " + options.getHost() + ". Refusing to start.");
        }
        return options;
    }

    /**
     * Uses Application Default Credentials: the service account attached to the container on Cloud Run, or
     * {@code GOOGLE_APPLICATION_CREDENTIALS} on other platforms. No key files are part of the application.
     */
    private static FirestoreOptions cloudOptions(FirestoreProperties properties) {
        try {
            return FirestoreOptions.newBuilder()
                    .setProjectId(properties.projectId())
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No Google credentials found for Firestore project " + properties.projectId()
                            + ". On Cloud Run attach a service account; elsewhere set GOOGLE_APPLICATION_CREDENTIALS.",
                    e);
        }
    }
}

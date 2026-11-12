package com.dokiments.support;

import org.testcontainers.utility.DockerImageName;

/**
 * Container images used by integration tests, pinned so test runs are reproducible. Keep them in line with
 * {@code compose.yaml}, so tests run against what local development uses.
 */
public final class TestImages {

    public static final DockerImageName REDIS = DockerImageName.parse("redis:8.10-alpine");

    /**
     * Built from {@code docker/firebase-emulators} by the {@code buildFirebaseEmulatorsImage} Gradle task before the
     * integration tests run, under the name {@code compose.yaml} gives it.
     */
    public static final DockerImageName FIREBASE_EMULATORS =
            DockerImageName.parse("dokiments-firebase-emulators:local");

    private TestImages() {}
}

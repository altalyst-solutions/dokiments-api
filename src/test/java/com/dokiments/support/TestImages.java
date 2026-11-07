package com.dokiments.support;

import org.testcontainers.utility.DockerImageName;

/**
 * Container images used by integration tests, pinned so test runs are reproducible. Keep them in line with
 * {@code compose.yaml}, so tests run against what local development uses.
 */
public final class TestImages {

    public static final DockerImageName REDIS = DockerImageName.parse("redis:8.10-alpine");

    public static final DockerImageName FIRESTORE_EMULATOR =
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:592.0.0-emulators");

    private TestImages() {}
}

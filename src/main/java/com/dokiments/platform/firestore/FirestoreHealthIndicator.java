package com.dokiments.platform.firestore;

import com.google.cloud.firestore.Firestore;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Reports whether Firestore answers, as the {@code firestore} entry of {@code /actuator/health} (Spring Boot names the
 * entry after the bean, without the {@code HealthIndicator} suffix).
 *
 * <p>It runs a query for at most one document in a collection that stays empty. On the real Firestore that is billed as
 * one read per health check, which is negligible. It is deliberately not part of the readiness probe
 * ({@code /actuator/health/readiness}): if Firestore is down, every instance is affected alike, and taking them all out
 * of service would turn a database problem into the whole API being unreachable.
 */
@Component
class FirestoreHealthIndicator extends AbstractHealthIndicator {

    /** Never written to. Only used to check that Firestore answers queries. */
    private static final String HEALTH_COLLECTION = "_health";

    /** Long enough for a slow but working Firestore, short enough not to hold up the health endpoint. */
    private static final long TIMEOUT_SECONDS = 3;

    private final Firestore firestore;

    private final FirestoreProperties properties;

    FirestoreHealthIndicator(Firestore firestore, FirestoreProperties properties) {
        super("Firestore health check failed");
        this.firestore = firestore;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        firestore.collection(HEALTH_COLLECTION).limit(1).get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        builder.up().withDetail("projectId", properties.projectId()).withDetail("emulator", properties.usesEmulator());
    }
}

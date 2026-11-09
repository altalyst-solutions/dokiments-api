package com.dokiments.platform.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.support.IntegrationTest;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.testcontainers.gcloud.FirestoreEmulatorContainer;

/** The application's own Firestore client, against the emulator, with the helpers adapters use. */
@IntegrationTest
class FirestoreIT {

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirestoreEmulatorContainer emulator;

    @Autowired
    @Qualifier("firestoreHealthIndicator") private HealthIndicator firestoreHealth;

    @Test
    void theClientTalksToTheEmulatorNeverToGoogle() {
        assertThat(firestore.getOptions().getHost()).isEqualTo(emulator.getEmulatorEndpoint());
        assertThat(firestore.getOptions().getProjectId()).startsWith(FirestoreProperties.EMULATOR_PROJECT_PREFIX);
    }

    @Test
    void writesAndReadsTheValueTypesTheApplicationStores() {
        Instant createdAt = Instant.parse("2026-07-18T10:15:30.123456Z");
        var document = firestore.collection("users").document(uniqueId());

        FirestoreCalls.await(document.set(Map.of(
                "email",
                "probe@example.com",
                "documentCount",
                3L,
                "createdAt",
                FirestoreCalls.toTimestamp(createdAt),
                "recipients",
                List.of(Map.of("email", "someone@example.com", "status", "pending")))));

        var snapshot = FirestoreCalls.await(document.get());

        assertThat(snapshot.getString("email")).isEqualTo("probe@example.com");
        assertThat(snapshot.getLong("documentCount")).isEqualTo(3L);
        var storedCreatedAt = snapshot.getTimestamp("createdAt");
        assertThat(storedCreatedAt).isNotNull();
        assertThat(FirestoreCalls.toInstant(storedCreatedAt)).isEqualTo(createdAt);
        assertThat(snapshot.get("recipients"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .singleElement()
                .isEqualTo(Map.of("email", "someone@example.com", "status", "pending"));
    }

    @Test
    void creatingADocumentThatAlreadyExistsReportsAlreadyExists() {
        var document = firestore.collection("users").document(uniqueId());
        FirestoreCalls.await(document.create(Map.of("email", "first@example.com")));

        assertThatThrownBy(() -> FirestoreCalls.await(document.create(Map.of("email", "second@example.com"))))
                .isInstanceOfSatisfying(
                        FirestoreCallException.class,
                        e -> assertThat(e.code()).isEqualTo(StatusCode.Code.ALREADY_EXISTS));
    }

    @Test
    void theHealthCheckReportsFirestoreAsUp() {
        var health = firestoreHealth.health(true);

        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("emulator", true);
    }

    /** Tests share the emulator within a run, so each uses its own document. */
    private static String uniqueId() {
        return UUID.randomUUID().toString();
    }
}

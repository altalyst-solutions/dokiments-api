package com.dokiments.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.gcloud.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the Firestore emulator setup works with the Google Cloud Firestore client that ships with the Firebase Admin
 * SDK, before any persistence adapter relies on it.
 *
 * <p>Uses the client directly: the application's own Firestore configuration arrives with the platform layer, at which
 * point the emulator moves into {@link TestcontainersConfiguration}.
 */
@Testcontainers
@Tag(IntegrationTest.TAG)
class FirestoreEmulatorIT {

    @Container
    private static final FirestoreEmulatorContainer EMULATOR =
            new FirestoreEmulatorContainer(TestImages.FIRESTORE_EMULATOR);

    private static Firestore firestore;

    @BeforeAll
    static void connect() {
        var options = FirestoreOptions.getDefaultInstance().toBuilder()
                // "demo-" project IDs are reserved for emulators: nothing can ever reach a real project.
                .setProjectId("demo-dokiments")
                // setHost (not setEmulatorHost, which this client version ignores here) is what
                // routes traffic to the emulator; without it the client calls firestore.googleapis.com.
                .setHost(EMULATOR.getEmulatorEndpoint())
                .setCredentials(NoCredentials.getInstance())
                .build();

        assertThat(options.getHost())
                .as("must talk to the local emulator, never to Google")
                .isEqualTo(EMULATOR.getEmulatorEndpoint());

        firestore = options.getService();
    }

    @AfterAll
    static void disconnect() throws Exception {
        firestore.close();
    }

    @Test
    void writesAndReadsADocumentWithTheValueTypesWeStore() throws Exception {
        var invitedAt = Timestamp.parseTimestamp("2026-07-18T00:00:00Z");
        var document = firestore.collection("users").document("probe-user");

        document.set(Map.of(
                        "email",
                        "probe@example.com",
                        "role",
                        "free",
                        "documentCount",
                        3L,
                        "createdAt",
                        invitedAt,
                        "recipients",
                        List.of(Map.of("email", "someone@example.com", "status", "pending"))))
                .get();

        var snapshot = document.get().get();

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.getString("email")).isEqualTo("probe@example.com");
        assertThat(snapshot.getLong("documentCount")).isEqualTo(3L);
        assertThat(snapshot.getTimestamp("createdAt")).isEqualTo(invitedAt);
        assertThat(snapshot.get("recipients"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .isEqualTo(Map.of("email", "someone@example.com", "status", "pending"));
    }
}

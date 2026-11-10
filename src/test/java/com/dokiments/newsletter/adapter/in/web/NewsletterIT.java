package com.dokiments.newsletter.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * {@code POST /api/newsletter} end to end: the web app's contract, the real security setup, and the document as it
 * lands in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class NewsletterIT {

    /** SHA-256 of {@code jane@example.com}, as the web app computes the document ID. */
    private static final String JANE = "8c87b489ce35cf2e2f39f80e282cb2e804932a56a213983eeeb428407d43b52d";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @BeforeEach
    void startWithoutJane() {
        FirestoreCalls.await(firestore.collection("newsletter").document(JANE).delete());
    }

    @Test
    void anAddressIsSubscribedWithoutSigningIn() {
        assertThat(subscribe("{\"email\": \" Jane@Example.com \"}"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"status\":\"subscribed\"}");

        var stored = FirestoreCalls.await(
                firestore.collection("newsletter").document(JANE).get());
        assertThat(stored.getData()).containsOnlyKeys("email", "status", "createdAt");
        assertThat(stored.getString("email")).isEqualTo("jane@example.com");
        assertThat(stored.getString("status")).isEqualTo("subscribed");
        var createdAt = stored.getTimestamp("createdAt");
        assertThat(createdAt).isNotNull();
        assertThat(FirestoreCalls.toInstant(createdAt)).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void subscribingAgainKeepsOneSubscription() {
        subscribe("{\"email\": \"jane@example.com\"}").assertThat().hasStatusOk();
        subscribe("{\"email\": \"JANE@example.com\"}").assertThat().hasStatusOk();

        var documents = FirestoreCalls.await(firestore
                .collection("newsletter")
                .whereEqualTo("email", "jane@example.com")
                .get());
        assertThat(documents.size()).isEqualTo(1);
    }

    @Test
    void noSessionIsCreated() {
        assertThat(subscribe("{\"email\": \"jane@example.com\"}"))
                .hasStatusOk()
                .headers()
                .doesNotContainHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void otherFieldsAreIgnored() {
        assertThat(subscribe("{\"email\": \"jane@example.com\", \"name\": \"Jane\"}"))
                .hasStatusOk();
    }

    @Test
    void anImplausibleAddressIsAnInvalidBody() {
        assertThat(subscribe("{\"email\": \"jane@example\"}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    @Test
    void aMissingAddressIsAnInvalidBody() {
        assertThat(subscribe("{}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    @Test
    void aBodyThatIsntJsonIsReportedAsSuch() {
        assertThat(subscribe("{"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Request body must be valid JSON.\"}");
        assertThat(subscribe("null"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Request body must be valid JSON.\"}");
    }

    @Test
    void onlyPostIsOpen() {
        assertThat(mvc.get().uri("/api/newsletter")).hasStatus(401);
    }

    private MockMvcTester.MockMvcRequestBuilder subscribe(String body) {
        return mvc.post()
                .uri("/api/newsletter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}

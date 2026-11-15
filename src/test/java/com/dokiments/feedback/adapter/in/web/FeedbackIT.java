package com.dokiments.feedback.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sending feedback end to end, as the web app does it: through the real security setup, with the session in Redis and
 * the feedback in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class FeedbackIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final String TOO_SOON = "{\"error\":\"Thanks — we just received your last message. Please wait a"
            + " moment before sending another.\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @Test
    void sendingFeedbackNeedsASession() {
        assertThat(send("{\"message\":\"Hi\",\"type\":\"feedback\"}", null))
                .hasStatus(401)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
    }

    @Test
    void feedbackIsKeptWithItsSenderFromTheSessionNotTheBody() {
        var jane = signUp("Jane Doe");

        MvcTestResult sent = send(
                "{\"message\":\"  The export does nothing.  \",\"path\":\" /my-documents \",\"type\":\"problem\","
                        + "\"uid\":\"someone-else\",\"email\":\"eve@example.com\"}",
                jane.session());

        assertThat(sent).hasStatus(201);
        JsonNode feedback = body(sent).path("feedback");
        String id = feedback.path("id").asString();
        long createdAt = feedback.path("createdAt").asLong();
        assertThat(id).matches("feedback_[0-9a-z]+_[0-9a-z]{6}");
        assertThat(sent)
                .bodyJson()
                .isStrictlyEqualTo("{\"feedback\":{\"createdAt\":" + createdAt + ",\"displayName\":\"Jane Doe\","
                        + "\"email\":\"" + jane.email() + "\",\"id\":\"" + id + "\","
                        + "\"message\":\"The export does nothing.\",\"path\":\"/my-documents\",\"type\":\"problem\","
                        + "\"uid\":\"" + jane.uid() + "\"}}");

        var stored = FirestoreCalls.await(
                firestore.collection("feedback").document(id).get());
        assertThat(stored.getData())
                .containsEntry("uid", jane.uid())
                .containsEntry("email", jane.email())
                .containsEntry("displayName", "Jane Doe")
                .containsEntry("message", "The export does nothing.")
                .containsEntry("path", "/my-documents")
                .containsEntry("type", "problem")
                .hasSize(7);
        var meta = FirestoreCalls.await(meta(jane.uid()).get());
        assertThat(meta.getLong("count")).isEqualTo(1);
        assertThat(meta.getTimestamp("lastFeedbackAt")).isEqualTo(stored.getTimestamp("createdAt"));
    }

    @Test
    void aSecondMessageWithinAMinuteIsRefusedAndNotKept() {
        var jane = signUp("Jane");
        assertThat(send("{\"message\":\"First\",\"type\":\"feedback\"}", jane.session()))
                .hasStatus(201);

        assertThat(send("{\"message\":\"Second\",\"type\":\"feedback\"}", jane.session()))
                .hasStatus(429)
                .bodyJson()
                .isStrictlyEqualTo(TOO_SOON);
        assertThat(feedbackFrom(jane.uid())).isEqualTo(1);
        assertThat(FirestoreCalls.await(meta(jane.uid()).get()).getLong("count"))
                .isEqualTo(1);
    }

    @Test
    void aMessageAMinuteAfterTheLastIsKeptAndCounted() {
        var jane = signUp("Jane");
        FirestoreCalls.await(meta(jane.uid())
                .set(Map.of(
                        "count",
                        4L,
                        "lastFeedbackAt",
                        FirestoreCalls.toTimestamp(Instant.now().minus(Duration.ofSeconds(61))),
                        "other",
                        "kept")));

        assertThat(send("{\"message\":\"Again\",\"type\":\"feedback\"}", jane.session()))
                .hasStatus(201);

        var meta = FirestoreCalls.await(meta(jane.uid()).get());
        assertThat(meta.getLong("count")).isEqualTo(5);
        assertThat(meta.getString("other")).isEqualTo("kept");
    }

    @Test
    void twoMessagesAtOnceCantBothPassTheThrottle() throws Exception {
        var jane = signUp("Jane");

        List<Callable<Integer>> sends = List.of(
                () -> send("{\"message\":\"One\",\"type\":\"feedback\"}", jane.session())
                        .getResponse()
                        .getStatus(),
                () -> send("{\"message\":\"Two\",\"type\":\"feedback\"}", jane.session())
                        .getResponse()
                        .getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (var answer : threads.invokeAll(sends)) {
                statuses.add(answer.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 429);
        assertThat(feedbackFrom(jane.uid())).isEqualTo(1);
    }

    @Test
    void invalidFeedbackIsRefusedAndNotKept() {
        var jane = signUp("Jane");

        for (String body : List.of(
                "{\"type\":\"feedback\"}",
                "{\"message\":\"   \",\"type\":\"feedback\"}",
                "{\"message\":\"" + "m".repeat(2001) + "\",\"type\":\"feedback\"}",
                "{\"message\":\"Hi\",\"path\":\"" + "p".repeat(513) + "\",\"type\":\"feedback\"}",
                "{\"message\":\"Hi\"}",
                "{\"message\":\"Hi\",\"type\":\"bug\"}",
                "{\"message\":5,\"type\":\"feedback\"}")) {
            assertThat(send(body, jane.session()))
                    .as(body.length() > 100 ? body.substring(0, 100) : body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
        }
        assertThat(feedbackFrom(jane.uid())).isZero();
    }

    // Helpers

    private record Account(String uid, String email, Cookie session) {}

    private Account signUp(String displayName) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult result = post(
                "/api/auth/sign-up",
                "{\"displayName\":\"" + displayName + "\",\"email\":\"" + email + "\",\"password\":\"secret123\"}",
                null);
        assertThat(result).hasStatusOk();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return new Account(body(result).path("user").path("uid").asString(), email, session);
    }

    private MvcTestResult send(String body, @Nullable Cookie session) {
        return post("/api/feedback", body, session);
    }

    /** A change as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult post(String path, String body, @Nullable Cookie session) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        List<Cookie> cookies = new ArrayList<>(List.of(csrf));
        if (session != null) {
            cookies.add(session);
        }
        return mvc.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies.toArray(Cookie[]::new))
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    private static JsonNode body(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private DocumentReference meta(String uid) {
        return firestore
                .collection("users")
                .document(uid)
                .collection("feedbackMeta")
                .document("latest");
    }

    private int feedbackFrom(String uid) {
        return FirestoreCalls.await(firestore
                        .collection("feedback")
                        .whereEqualTo("uid", uid)
                        .get())
                .size();
    }
}

package com.dokiments.notifications.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The notifications end to end, as the web app calls them: through the real security setup, with the session in Redis,
 * and the catalog and the time last seen in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class NotificationsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    private final Instant now = Instant.now();

    /** The catalog is shared by every test class, so this test starts from an empty one and leaves it empty. */
    @BeforeEach
    @AfterEach
    void emptyTheCatalog() {
        for (String collection : List.of("templateStyles", "templates")) {
            for (var entry :
                    FirestoreCalls.await(firestore.collection(collection).get()).getDocuments()) {
                FirestoreCalls.await(entry.getReference().delete());
            }
        }
    }

    @Test
    void theRoutesNeedASession() {
        for (var call : List.of(
                call(HttpMethod.GET, "/api/notifications", null),
                call(HttpMethod.POST, "/api/notifications/read", null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    @Test
    void newTemplatesOfTheLastTwoWeeksAreListedNewestFirstAndUnread() {
        writeStyle();
        var invoiceAdded = now.minus(Duration.ofDays(1));
        writeTemplate("classic-invoice", "Invoice", invoiceAdded);
        writeTemplate("classic-old", "Old", now.minus(Duration.ofDays(20)));
        writeTemplate("classic-seeded", "Seeded", null);
        var batchStart = now.minus(Duration.ofHours(3));
        writeTemplate("classic-a", "First", batchStart);
        writeTemplate("classic-b", "Second", batchStart.plus(Duration.ofMinutes(5)));

        MvcTestResult listed = call(HttpMethod.GET, "/api/notifications", signUp());

        long batchAt = batchStart.plus(Duration.ofMinutes(5)).toEpochMilli();
        assertThat(listed)
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"notifications\":["
                        + "{\"action\":{\"href\":\"/marketplace\",\"label\":\"View in marketplace\"},"
                        + "\"body\":\"First, Second\",\"createdAt\":" + batchAt + ",\"id\":\"template:classic-a\","
                        + "\"kind\":\"template\",\"read\":false,\"title\":\"2 new templates added\"},"
                        + "{\"action\":{\"href\":\"/marketplace\",\"label\":\"View in marketplace\"},"
                        + "\"body\":\"Classic designs style · free plan\",\"createdAt\":" + invoiceAdded.toEpochMilli()
                        + ",\"id\":\"template:classic-invoice\",\"kind\":\"template\",\"read\":false,"
                        + "\"title\":\"New template: Invoice\"}]}");
    }

    @Test
    void markingReadCountsFromNowAndALaterTemplateIsUnreadAgain() {
        writeStyle();
        writeTemplate("classic-invoice", "Invoice", now.minus(Duration.ofHours(1)));
        var jane = signUp();

        MvcTestResult marked = call(HttpMethod.POST, "/api/notifications/read", jane);

        assertThat(marked).hasStatusOk();
        long lastSeenAt = body(marked).path("lastSeenAt").asLong();
        assertThat(marked).bodyJson().isStrictlyEqualTo("{\"lastSeenAt\":" + lastSeenAt + "}");
        assertThat(read(jane)).containsExactly(true);

        writeTemplate(
                "classic-receipt", "Receipt", Instant.ofEpochMilli(lastSeenAt).plusSeconds(1));
        assertThat(read(jane)).containsExactly(false, true);
    }

    @Test
    void theTimeLastSeenIsStoredWithoutTouchingOtherFields() {
        var jane = signUp();
        String uid = uid(jane);
        FirestoreCalls.await(state(uid).set(Map.of("other", "kept")));

        long lastSeenAt = body(call(HttpMethod.POST, "/api/notifications/read", jane))
                .path("lastSeenAt")
                .asLong();

        var stored = FirestoreCalls.await(state(uid).get());
        assertThat(stored.getString("other")).isEqualTo("kept");
        assertThat(FirestoreCalls.toInstant(Objects.requireNonNull(stored.getTimestamp("lastSeenAt")))
                        .toEpochMilli())
                .isEqualTo(lastSeenAt);
    }

    @Test
    void anEmptyCatalogMeansNoNotifications() {
        assertThat(call(HttpMethod.GET, "/api/notifications", signUp()))
                .bodyJson()
                .isStrictlyEqualTo("{\"notifications\":[]}");
    }

    // Helpers

    private Cookie signUp() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult result = call(
                HttpMethod.POST,
                "/api/auth/sign-up",
                null,
                "{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\"}");
        assertThat(result).hasStatusOk();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private String uid(Cookie session) {
        return body(call(HttpMethod.GET, "/api/auth/me", session))
                .path("user")
                .path("uid")
                .asString();
    }

    private List<Boolean> read(Cookie session) {
        return body(call(HttpMethod.GET, "/api/notifications", session))
                .path("notifications")
                .valueStream()
                .map(notification -> notification.path("read").asBoolean())
                .toList();
    }

    private MvcTestResult call(HttpMethod method, String path, @Nullable Cookie session) {
        return call(method, path, session, null);
    }

    /** A request as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult call(HttpMethod method, String path, @Nullable Cookie session, @Nullable String body) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        List<Cookie> cookies = new ArrayList<>(List.of(csrf));
        if (session != null) {
            cookies.add(session);
        }
        var request = mvc.method(method)
                .uri(path)
                .cookie(cookies.toArray(Cookie[]::new))
                .header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static JsonNode body(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private void writeStyle() {
        FirestoreCalls.await(firestore
                .document("templateStyles/classic")
                .set(Map.of(
                        "name",
                        "Classic designs",
                        "description",
                        "",
                        "tier",
                        "free",
                        "sortOrder",
                        0L,
                        "isActive",
                        true)));
    }

    private void writeTemplate(String id, String name, @Nullable Instant createdAt) {
        Map<String, Object> fields = new HashMap<>(Map.of(
                "name", name,
                "description", "",
                "documentType", "invoice",
                "styleId", "classic",
                "tier", "free",
                "isActive", true,
                "fields", List.of()));
        if (createdAt != null) {
            fields.put("createdAt", FirestoreCalls.toTimestamp(createdAt));
        }
        FirestoreCalls.await(firestore.document("templates/" + id).set(fields));
    }

    private DocumentReference state(String uid) {
        return firestore
                .collection("users")
                .document(uid)
                .collection("notificationMeta")
                .document("state");
    }
}

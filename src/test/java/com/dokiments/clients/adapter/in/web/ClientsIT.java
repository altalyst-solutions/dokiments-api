package com.dokiments.clients.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * The clients routes end to end, as the web app calls them: through the real security setup, with the session in Redis
 * and the clients in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ClientsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final String INVALID_BODY = "{\"error\":\"Invalid request body.\"}";
    private static final String JANE =
            "{\"name\":\"Jane\",\"companyName\":\"Acme\",\"email\":\"jane@acme.example\"," + "\"phone\":\"555\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    // Access

    @Test
    void everyRouteNeedsASession() {
        for (var call : List.of(
                call(HttpMethod.GET, "/api/clients", null, null),
                call(HttpMethod.POST, "/api/clients", JANE, null),
                call(HttpMethod.PATCH, "/api/clients/client_x", "{\"name\":\"Jo\"}", null),
                call(HttpMethod.DELETE, "/api/clients/client_x", null, null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    @Test
    void aChangeWithoutTheCsrfTokenIsForbidden() {
        Cookie session = signUp().session();

        assertThat(mvc.post()
                        .uri("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JANE)
                        .cookie(session)
                        .exchange())
                .hasStatus(403);
    }

    // Adding and listing

    @Test
    void anAddedClientIsAnsweredWithItsIdAndStoredUnderTheUser() {
        var jane = signUp();

        MvcTestResult added = call(
                HttpMethod.POST,
                "/api/clients",
                "{\"name\":\" Jane \",\"companyName\":\"Acme\",\"email\":\"jane@acme.example\",\"phone\":\"555\","
                        + "\"brn\":\"C123\",\"extra\":\"ignored\"}",
                jane.session());

        assertThat(added).hasStatus(201);
        JsonNode client = body(added).path("client");
        String id = client.path("id").asString();
        assertThat(id).matches("client_[0-9a-z]+_[0-9a-z]{6}");
        long createdAt = client.path("createdAt").asLong();
        assertThat(createdAt).isCloseTo(System.currentTimeMillis(), org.assertj.core.data.Offset.offset(60_000L));
        assertThat(added)
                .bodyJson()
                .isStrictlyEqualTo("{\"client\":{\"address\":\"\",\"brn\":\"C123\",\"companyName\":\"Acme\","
                        + "\"createdAt\":" + createdAt + ",\"email\":\"jane@acme.example\",\"id\":\"" + id + "\","
                        + "\"name\":\"Jane\",\"nationalId\":\"\",\"phone\":\"555\",\"updatedAt\":" + createdAt + "}}");

        var stored = FirestoreCalls.await(client(jane.uid(), id).get());
        assertThat(stored.getString("name")).isEqualTo("Jane");
        assertThat(stored.getString("address")).isEmpty();
        assertThat(stored.getTimestamp("createdAt")).isEqualTo(stored.getTimestamp("updatedAt"));
        assertThat(stored.contains("extra")).isFalse();
        assertThat(stored.getData()).hasSize(9);

        assertThat(call(HttpMethod.GET, "/api/clients", null, jane.session()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"clients\":[" + client + "]}");
    }

    @Test
    void theListHoldsOnlyTheUsersOwnClientsNewestFirst() {
        var jane = signUp();
        var eve = signUp();
        store(jane.uid(), "client_a_older", Map.of("name", "Old", "createdAt", time("2026-01-01T00:00:00Z")));
        store(jane.uid(), "client_b_newer", Map.of("name", "New", "createdAt", time("2026-02-01T00:00:00Z")));
        store(eve.uid(), "client_eve", Map.of("name", "Eve", "createdAt", time("2026-03-01T00:00:00Z")));

        JsonNode clients =
                body(call(HttpMethod.GET, "/api/clients", null, jane.session())).path("clients");

        assertThat(clients.valueStream().map(c -> c.path("id").asString()))
                .containsExactly("client_b_newer", "client_a_older");
        assertThat(call(HttpMethod.GET, "/api/clients", null, signUp().session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"clients\":[]}");
    }

    @Test
    void anOlderClientWithMissingFieldsIsReadAsEmpty() {
        var jane = signUp();
        store(jane.uid(), "client_legacy", Map.of("name", "Jane", "phone", 555L));

        assertThat(call(HttpMethod.GET, "/api/clients", null, jane.session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"clients\":[{\"address\":\"\",\"brn\":\"\",\"companyName\":\"\",\"createdAt\":0,"
                        + "\"email\":\"\",\"id\":\"client_legacy\",\"name\":\"Jane\",\"nationalId\":\"\","
                        + "\"phone\":\"\",\"updatedAt\":0}]}");
    }

    @Test
    void anInvalidNewClientIsRefusedAndNothingIsStored() {
        var jane = signUp();

        for (String body : List.of(
                "{\"companyName\":\"Acme\",\"email\":\"jane@acme.example\",\"phone\":\"555\"}",
                "{\"name\":\" \",\"companyName\":\"Acme\",\"email\":\"jane@acme.example\",\"phone\":\"555\"}",
                "{\"name\":\"Jane\",\"companyName\":\"Acme\",\"email\":\"jane@acme\",\"phone\":\"555\"}",
                "{\"name\":\"Jane\",\"companyName\":\"Acme\",\"email\":\"jane@acme.example\",\"phone\":555}",
                "{\"name\":\"" + "n".repeat(201) + "\",\"companyName\":\"Acme\",\"email\":\"j@a.co\",\"phone\":\"5\"}",
                "[]")) {
            assertThat(call(HttpMethod.POST, "/api/clients", body, jane.session()))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_BODY);
        }
        assertThat(FirestoreCalls.await(clients(jane.uid()).get()).isEmpty()).isTrue();
    }

    // Changing

    @Test
    void aChangeWritesOnlyTheFieldsSent() {
        var jane = signUp();
        JsonNode added = body(call(HttpMethod.POST, "/api/clients", JANE, jane.session()))
                .path("client");
        String id = added.path("id").asString();

        MvcTestResult changed =
                call(HttpMethod.PATCH, "/api/clients/" + id, "{\"phone\":\" 777 \",\"address\":\"\"}", jane.session());

        assertThat(changed).hasStatusOk();
        JsonNode client = body(changed).path("client");
        assertThat(client.path("phone").asString()).isEqualTo("777");
        assertThat(client.path("name").asString()).isEqualTo("Jane");
        assertThat(client.path("createdAt").asLong())
                .isEqualTo(added.path("createdAt").asLong());
        assertThat(client.path("updatedAt").asLong())
                .isGreaterThanOrEqualTo(added.path("updatedAt").asLong());
        var stored = FirestoreCalls.await(client(jane.uid(), id).get());
        assertThat(stored.getString("phone")).isEqualTo("777");
        assertThat(stored.getString("email")).isEqualTo("jane@acme.example");
        // Firestore keeps microseconds; the answer has milliseconds, as the web app's does.
        assertThat(FirestoreCalls.toInstant(Objects.requireNonNull(stored.getTimestamp("updatedAt")))
                        .toEpochMilli())
                .isEqualTo(client.path("updatedAt").asLong());
    }

    @Test
    void anInvalidChangeIsRefused() {
        var jane = signUp();
        String id = body(call(HttpMethod.POST, "/api/clients", JANE, jane.session()))
                .path("client")
                .path("id")
                .asString();

        for (String body : List.of(
                "{}",
                "{\"other\":\"x\"}",
                "{\"name\":\"\"}",
                "{\"email\":\"x\"}",
                "{\"name\":null}",
                "{\"phone\":5}")) {
            assertThat(call(HttpMethod.PATCH, "/api/clients/" + id, body, jane.session()))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_BODY);
        }
    }

    @Test
    void aClientThatIsntTheUsersOwnIsNotFound() {
        var eve = signUp();
        store(eve.uid(), "client_eve", Map.of("name", "Eve"));
        var jane = signUp();

        for (String id : List.of("client_eve", "client_missing", "__name__")) {
            assertThat(call(HttpMethod.PATCH, "/api/clients/" + id, "{\"name\":\"Mallory\"}", jane.session()))
                    .as(id)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Client not found.\"}");
        }
        assertThat(FirestoreCalls.await(client(eve.uid(), "client_eve").get()).getString("name"))
                .isEqualTo("Eve");
        assertThat(FirestoreCalls.await(client(jane.uid(), "client_missing").get())
                        .exists())
                .isFalse();
    }

    // Removing

    @Test
    void aRemovedClientIsGoneAndRemovingAgainIsFine() {
        var jane = signUp();
        String id = body(call(HttpMethod.POST, "/api/clients", JANE, jane.session()))
                .path("client")
                .path("id")
                .asString();

        assertThat(call(HttpMethod.DELETE, "/api/clients/" + id, null, jane.session()))
                .hasStatus(204);
        assertThat(call(HttpMethod.DELETE, "/api/clients/" + id, null, jane.session()))
                .hasStatus(204);
        assertThat(FirestoreCalls.await(client(jane.uid(), id).get()).exists()).isFalse();
    }

    @Test
    void removingSomeoneElsesClientChangesNothing() {
        var eve = signUp();
        store(eve.uid(), "client_eve", Map.of("name", "Eve"));

        MvcTestResult removed = call(HttpMethod.DELETE, "/api/clients/client_eve", null, signUp().session());

        assertThat(removed).hasStatus(204);
        assertThat(removed.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(FirestoreCalls.await(client(eve.uid(), "client_eve").get()).exists())
                .isTrue();
    }

    // Helpers

    private record Account(String uid, Cookie session) {}

    private Account signUp() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult result = call(
                HttpMethod.POST,
                "/api/auth/sign-up",
                "{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\"}",
                null);
        assertThat(result).hasStatusOk();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return new Account(body(result).path("user").path("uid").asString(), session);
    }

    /** A request as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult call(HttpMethod method, String path, @Nullable String body, @Nullable Cookie session) {
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

    private void store(String uid, String id, Map<String, Object> fields) {
        FirestoreCalls.await(client(uid, id).set(fields));
    }

    private com.google.cloud.firestore.CollectionReference clients(String uid) {
        return firestore.collection("users").document(uid).collection("clients");
    }

    private DocumentReference client(String uid, String id) {
        return clients(uid).document(id);
    }

    private static Timestamp time(String instant) {
        return FirestoreCalls.toTimestamp(Instant.parse(instant));
    }
}

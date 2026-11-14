package com.dokiments.documents.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * The documents routes end to end, as the web app calls them: through the real security setup, with the session in
 * Redis, and the catalog and documents in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class DocumentsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final String TEMPLATE = "documents-it-invoice";
    private static final String INVALID_BODY = "{\"error\":\"Invalid request body.\"}";
    private static final String NOT_FOUND = "{\"error\":\"Document not found.\"}";
    private static final String SNAPSHOT = "{\"description\":\"A billing sheet\",\"documentType\":\"invoice\","
            + "\"fields\":[{\"key\":\"client\",\"label\":\"Client\",\"placeholder\":\"Acme Ltd\",\"type\":\"text\"},"
            + "{\"key\":\"due\",\"label\":\"Due\",\"type\":\"date\"}],\"name\":\"Standard Invoice\","
            + "\"style\":{\"description\":\"Clean\",\"id\":\"classic\",\"name\":\"Classic\",\"tier\":\"free\"}}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @BeforeEach
    void writeTheTemplate() {
        write(
                "templateStyles/classic",
                Map.of("name", "Classic", "description", "Clean", "tier", "free", "sortOrder", 0L, "isActive", true));
        write("templates/" + TEMPLATE, template("Standard Invoice", true));
        write("templates/documents-it-retired", template("Retired", false));
    }

    /** The catalog is shared by every test class, so these entries don't stay in it. */
    @AfterEach
    void removeTheTemplates() {
        for (String id : List.of(TEMPLATE, "documents-it-retired")) {
            FirestoreCalls.await(firestore.document("templates/" + id).delete());
        }
    }

    // Access

    @Test
    void everyRouteNeedsASession() {
        for (var call : List.of(
                call(HttpMethod.GET, "/api/documents", null, null),
                call(HttpMethod.POST, "/api/documents", newDocument("For Acme"), null),
                call(HttpMethod.GET, "/api/documents/doc_x", null, null),
                call(HttpMethod.PATCH, "/api/documents/doc_x", "{\"name\":\"x\"}", null),
                call(HttpMethod.DELETE, "/api/documents/doc_x", null, null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    // Making a document

    @Test
    void aNewDocumentIsAnsweredWithACopyOfItsTemplateAndStoredUnderTheUser() {
        var jane = signUp();

        MvcTestResult created = call(HttpMethod.POST, "/api/documents", newDocument(" For Acme "), jane.session());

        assertThat(created).hasStatus(201);
        JsonNode document = body(created).path("document");
        String id = document.path("id").asString();
        long createdAt = document.path("createdAt").asLong();
        assertThat(id).matches("doc_[0-9a-z]+_[0-9a-z]{6}");
        assertThat(created)
                .bodyJson()
                .isStrictlyEqualTo("{\"document\":{\"createdAt\":" + createdAt + ",\"id\":\"" + id + "\","
                        + "\"name\":\" For Acme \",\"templateId\":\"" + TEMPLATE + "\",\"templateSnapshot\":"
                        + SNAPSHOT + ",\"updatedAt\":" + createdAt
                        + ",\"values\":{\"client\":\"Acme\",\"due\":\"\"}}}");

        var stored = FirestoreCalls.await(documents(jane.uid()).document(id).get());
        assertThat(stored.getString("name")).isEqualTo(" For Acme ");
        assertThat(stored.get("values")).isEqualTo(Map.of("client", "Acme", "due", ""));
        assertThat(stored.get("templateSnapshot.style.id")).isEqualTo("classic");
        assertThat(stored.getTimestamp("createdAt")).isEqualTo(stored.getTimestamp("updatedAt"));
        assertThat(FirestoreCalls.toInstant(Objects.requireNonNull(stored.getTimestamp("createdAt")))
                        .toEpochMilli())
                .isEqualTo(createdAt);
    }

    @Test
    void aDocumentKeepsItsCopyWhenTheCatalogChanges() {
        var jane = signUp();
        String id = create(jane.session());

        write("templates/" + TEMPLATE, template("Renamed in the catalog", true));

        assertThat(body(call(HttpMethod.GET, "/api/documents/" + id, null, jane.session()))
                        .path("document")
                        .path("templateSnapshot")
                        .path("name")
                        .asString())
                .isEqualTo("Standard Invoice");
    }

    @Test
    void onlyATemplateOfTheCatalogCanBeUsed() {
        Cookie session = signUp().session();

        for (String templateId : List.of("documents-it-retired", "documents-it-nope")) {
            assertThat(call(
                            HttpMethod.POST,
                            "/api/documents",
                            "{\"name\":\"x\",\"templateId\":\"" + templateId + "\",\"values\":{}}",
                            session))
                    .as(templateId)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Template not found.\"}");
        }
    }

    @Test
    void anInvalidNewDocumentIsRefusedAndNothingIsStored() {
        var jane = signUp();
        String fiftyOne =
                IntStream.range(0, 51).mapToObj(i -> "\"k" + i + "\":\"v\"").collect(Collectors.joining(",", "{", "}"));

        for (String body : List.of(
                "{\"templateId\":\"" + TEMPLATE + "\",\"values\":{}}",
                "{\"name\":\"\",\"templateId\":\"" + TEMPLATE + "\",\"values\":{}}",
                "{\"name\":\"" + "n".repeat(201) + "\",\"templateId\":\"" + TEMPLATE + "\",\"values\":{}}",
                "{\"name\":\"x\",\"templateId\":\"\",\"values\":{}}",
                "{\"name\":\"x\",\"values\":{}}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\"}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\",\"values\":[]}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\",\"values\":{\"a\":1}}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\",\"values\":{\"a\":null}}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\",\"values\":{\"a\":\"" + "x".repeat(20_001)
                        + "\"}}",
                "{\"name\":\"x\",\"templateId\":\"" + TEMPLATE + "\",\"values\":" + fiftyOne + "}")) {
            assertThat(call(HttpMethod.POST, "/api/documents", body, jane.session()))
                    .as(body.length() > 120 ? body.substring(0, 120) : body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_BODY);
        }
        assertThat(FirestoreCalls.await(documents(jane.uid()).get()).isEmpty()).isTrue();
    }

    // Reading

    @Test
    void theListHoldsOnlyTheUsersOwnOptionallyByTemplate() {
        var jane = signUp();
        var eve = signUp();
        write("users/" + jane.uid() + "/documents/doc_b", Map.of("name", "Contract", "templateId", "classic-contract"));
        write("users/" + jane.uid() + "/documents/doc_a", Map.of("name", "Invoice", "templateId", TEMPLATE));
        write("users/" + eve.uid() + "/documents/doc_c", Map.of("name", "Eve's", "templateId", TEMPLATE));

        assertThat(ids(call(HttpMethod.GET, "/api/documents", null, jane.session())))
                .containsExactly("doc_a", "doc_b");
        assertThat(ids(call(HttpMethod.GET, "/api/documents?templateId=classic-contract", null, jane.session())))
                .containsExactly("doc_b");
        assertThat(call(HttpMethod.GET, "/api/documents", null, signUp().session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"documents\":[]}");
        assertThat(call(HttpMethod.GET, "/api/documents?templateId=", null, jane.session()))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid query parameters.\"}");
    }

    @Test
    void anOlderDocumentWithoutACopyOrFieldsIsReadAsEmpty() {
        var jane = signUp();
        write("users/" + jane.uid() + "/documents/doc_old", Map.of("values", Map.of("a", "x", "b", 5L)));
        write(
                "users/" + jane.uid() + "/documents/doc_partial",
                Map.of("name", "Partial", "templateSnapshot", Map.of("name", "Old")));

        assertThat(call(HttpMethod.GET, "/api/documents/doc_old", null, jane.session()))
                .bodyJson()
                .isStrictlyEqualTo(
                        "{\"document\":{\"createdAt\":0,\"id\":\"doc_old\",\"name\":\"\",\"templateId\":\"\","
                                + "\"updatedAt\":0,\"values\":{\"a\":\"x\",\"b\":\"\"}}}");
        assertThat(body(call(HttpMethod.GET, "/api/documents/doc_partial", null, jane.session()))
                        .path("document")
                        .path("templateSnapshot")
                        .toString())
                .isEqualTo("{\"description\":\"\",\"documentType\":\"contract\",\"fields\":[],\"name\":\"Old\","
                        + "\"style\":{\"description\":\"\",\"id\":\"classic\",\"name\":\"\",\"tier\":\"free\"}}");
    }

    @Test
    void aDocumentThatIsntTheUsersOwnIsNotFound() {
        var eve = signUp();
        write("users/" + eve.uid() + "/documents/doc_eve", Map.of("name", "Eve's"));
        var jane = signUp();

        for (String id : List.of("doc_eve", "doc_missing", "__name__")) {
            assertThat(call(HttpMethod.GET, "/api/documents/" + id, null, jane.session()))
                    .as(id)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo(NOT_FOUND);
            assertThat(call(HttpMethod.PATCH, "/api/documents/" + id, "{\"name\":\"Mine\"}", jane.session()))
                    .as(id)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo(NOT_FOUND);
        }
        assertThat(FirestoreCalls.await(documents(eve.uid()).document("doc_eve").get())
                        .getString("name"))
                .isEqualTo("Eve's");
        assertThat(FirestoreCalls.await(
                                documents(jane.uid()).document("doc_missing").get())
                        .exists())
                .isFalse();
    }

    // Changing

    @Test
    void aChangeOfNameKeepsTheValuesAndNewValuesReplaceThemAll() {
        var jane = signUp();
        String id = create(jane.session());
        JsonNode before = body(call(HttpMethod.GET, "/api/documents/" + id, null, jane.session()))
                .path("document");

        JsonNode renamed = body(call(
                        HttpMethod.PATCH, "/api/documents/" + id, "{\"name\":\"Renamed\"}", jane.session()))
                .path("document");
        assertThat(renamed.path("name").asString()).isEqualTo("Renamed");
        assertThat(renamed.path("values")).isEqualTo(before.path("values"));
        assertThat(renamed.path("templateSnapshot")).isEqualTo(before.path("templateSnapshot"));
        assertThat(renamed.path("createdAt").asLong())
                .isEqualTo(before.path("createdAt").asLong());
        assertThat(renamed.path("updatedAt").asLong())
                .isGreaterThanOrEqualTo(before.path("updatedAt").asLong());

        JsonNode refilled = body(call(
                        HttpMethod.PATCH,
                        "/api/documents/" + id,
                        "{\"values\":{\"due\":\"2026-12-01\"}}",
                        jane.session()))
                .path("document");
        assertThat(refilled.path("values").toString()).isEqualTo("{\"due\":\"2026-12-01\"}");
        assertThat(refilled.path("name").asString()).isEqualTo("Renamed");
        assertThat(FirestoreCalls.await(documents(jane.uid()).document(id).get())
                        .get("values"))
                .isEqualTo(Map.of("due", "2026-12-01"));
    }

    @Test
    void anInvalidChangeIsRefused() {
        var jane = signUp();
        String id = create(jane.session());

        for (String body : List.of("{}", "{\"name\":\"\"}", "{\"values\":{\"a\":1}}", "{\"templateId\":\"other\"}")) {
            assertThat(call(HttpMethod.PATCH, "/api/documents/" + id, body, jane.session()))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_BODY);
        }
    }

    // Removing

    @Test
    void removingIsQuietAndOnlyAffectsTheUsersOwn() {
        var jane = signUp();
        var eve = signUp();
        String id = create(jane.session());
        write("users/" + eve.uid() + "/documents/" + id, Map.of("name", "Eve's"));

        assertThat(call(HttpMethod.DELETE, "/api/documents/" + id, null, jane.session()))
                .hasStatus(204);
        assertThat(call(HttpMethod.DELETE, "/api/documents/" + id, null, jane.session()))
                .hasStatus(204);

        assertThat(FirestoreCalls.await(documents(jane.uid()).document(id).get())
                        .exists())
                .isFalse();
        assertThat(FirestoreCalls.await(documents(eve.uid()).document(id).get()).exists())
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

    private static String newDocument(String name) {
        return "{\"name\":\"" + name + "\",\"templateId\":\"" + TEMPLATE
                + "\",\"values\":{\"client\":\"Acme\",\"due\":\"\"},\"extra\":\"ignored\"}";
    }

    private String create(Cookie session) {
        MvcTestResult created = call(HttpMethod.POST, "/api/documents", newDocument("For Acme"), session);
        assertThat(created).hasStatus(201);
        return body(created).path("document").path("id").asString();
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

    private static List<String> ids(MvcTestResult result) {
        return body(result)
                .path("documents")
                .valueStream()
                .map(document -> document.path("id").asString())
                .toList();
    }

    private static Map<String, Object> template(String name, boolean active) {
        return Map.of(
                "name",
                name,
                "description",
                "A billing sheet",
                "documentType",
                "invoice",
                "styleId",
                "classic",
                "tier",
                "free",
                "sortOrder",
                100L,
                "isActive",
                active,
                "fields",
                List.of(
                        Map.of("key", "client", "label", "Client", "placeholder", "Acme Ltd", "type", "text"),
                        Map.of("key", "due", "label", "Due", "type", "date")));
    }

    private void write(String path, Map<String, Object> fields) {
        FirestoreCalls.await(firestore.document(path).set(fields));
    }

    private CollectionReference documents(String uid) {
        return firestore.collection("users").document(uid).collection("documents");
    }
}

package com.dokiments.templates.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
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
 * The catalog and saved templates end to end, as the web app calls them: through the real security setup, with the
 * session in Redis and the catalog and libraries in the Firestore emulator. Each test writes the same small catalog, in
 * the shape the web app's seed script writes it.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TemplatesIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.006Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @BeforeEach
    void writeTheCatalog() {
        style("classic", "free", 1L, true);
        style("modern", "silver", 2L, true);
        style("brutalist", "gold", 3L, true);
        style("minimalist", "free", 4L, false);
        var invoice = template("classic-invoice", "classic", "free", 2L, true);
        invoice.put("createdAt", FirestoreCalls.toTimestamp(CREATED));
        invoice.put(
                "fields",
                List.of(
                        Map.of("key", "client", "label", "Client", "placeholder", "Acme Ltd", "type", "text"),
                        Map.of("key", "due", "label", "Due", "type", "date")));
        write("templates/classic-invoice", invoice);
        write("templates/classic-contract", template("classic-contract", "classic", "free", 1L, true));
        write("templates/modern-invoice", template("modern-invoice", "modern", "silver", 3L, true));
        write("templates/brutalist-invoice", template("brutalist-invoice", "brutalist", "gold", 4L, true));
        write("templates/minimalist-invoice", template("minimalist-invoice", "minimalist", "free", 5L, true));
        write("templates/classic-retired", template("classic-retired", "classic", "free", 6L, false));
        var unordered = template("classic-unordered", "classic", "free", null, true);
        write("templates/classic-unordered", unordered);
    }

    // Access

    @Test
    void everyRouteNeedsASession() {
        for (var call : List.of(
                call(HttpMethod.GET, "/api/templates", null, null),
                call(HttpMethod.GET, "/api/templates/classic-invoice", null, null),
                call(HttpMethod.GET, "/api/saved-templates", null, null),
                call(HttpMethod.POST, "/api/saved-templates", "{\"templateId\":\"classic-invoice\"}", null),
                call(HttpMethod.DELETE, "/api/saved-templates/classic-invoice", null, null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    // The catalog

    @Test
    void theCatalogHasTheActiveStylesAndTemplatesInTheirOrder() {
        JsonNode catalog = body(call(HttpMethod.GET, "/api/templates", null, signUp().session()));

        assertThat(ids(catalog.path("styles"))).containsExactly("classic", "modern", "brutalist");
        assertThat(ids(catalog.path("templates")))
                .containsExactly(
                        "classic-contract",
                        "classic-invoice",
                        "modern-invoice",
                        "brutalist-invoice",
                        "classic-unordered");
    }

    @Test
    void theTemplatesCanBeFilteredWhileTheStylesStayComplete() {
        Cookie session = signUp().session();

        JsonNode classic = body(call(HttpMethod.GET, "/api/templates?styleId=classic", null, session));
        assertThat(ids(classic.path("templates")))
                .containsExactly("classic-contract", "classic-invoice", "classic-unordered");
        assertThat(ids(classic.path("styles"))).hasSize(3);
        assertThat(ids(body(call(HttpMethod.GET, "/api/templates?tier=gold", null, session))
                        .path("templates")))
                .containsExactly("brutalist-invoice");
        assertThat(ids(body(call(HttpMethod.GET, "/api/templates?styleId=modern&tier=free", null, session))
                        .path("templates")))
                .isEmpty();

        for (String query : List.of("styleId=retro", "styleId=", "tier=platinum", "tier=Gold")) {
            assertThat(call(HttpMethod.GET, "/api/templates?" + query, null, session))
                    .as(query)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid query parameters.\"}");
        }
    }

    @Test
    void aTemplateIsAnsweredWithItsStyleAndFields() {
        Cookie session = signUp().session();

        assertThat(call(HttpMethod.GET, "/api/templates/classic-invoice", null, session))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"template\":{\"createdAt\":" + CREATED.toEpochMilli() + ","
                        + "\"description\":\"About classic-invoice\",\"documentType\":\"invoice\","
                        + "\"fields\":[{\"key\":\"client\",\"label\":\"Client\",\"placeholder\":\"Acme Ltd\","
                        + "\"type\":\"text\"},{\"key\":\"due\",\"label\":\"Due\",\"type\":\"date\"}],"
                        + "\"id\":\"classic-invoice\",\"name\":\"Name of classic-invoice\","
                        + "\"style\":{\"description\":\"About classic\",\"id\":\"classic\",\"name\":\"Name of classic\","
                        + "\"tier\":\"free\"},\"tier\":\"free\"}}");
        assertThat(body(call(HttpMethod.GET, "/api/templates/classic-contract", null, session))
                        .path("template")
                        .has("createdAt"))
                .isFalse();
    }

    @Test
    void aHiddenOrUnknownTemplateIsNotFound() {
        Cookie session = signUp().session();

        for (String id : List.of("classic-retired", "minimalist-invoice", "classic-nope")) {
            assertThat(call(HttpMethod.GET, "/api/templates/" + id, null, session))
                    .as(id)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Template not found.\"}");
        }
    }

    // Saving

    @Test
    void aSavedTemplateIsAnsweredAndStoredWithItsTime() {
        var jane = signUp();

        MvcTestResult saved = save(jane.session(), "classic-invoice");

        assertThat(saved).hasStatus(201);
        long savedAt = body(saved).path("savedAt").asLong();
        assertThat(saved)
                .bodyJson()
                .isStrictlyEqualTo("{\"savedAt\":" + savedAt
                        + ",\"savedId\":\"classic-invoice\",\"templateId\":\"classic-invoice\"}");
        var stored = FirestoreCalls.await(savedInvoice(jane.uid()).get());
        assertThat(stored.getData()).isEqualTo(Map.of("savedAt", savedAt));

        MvcTestResult again = save(jane.session(), "classic-invoice");
        assertThat(again).hasStatus(200);
        assertThat(body(again).path("savedAt").asLong()).isGreaterThanOrEqualTo(savedAt);
    }

    @Test
    void aFreeAccountKeepsAtMostTwo() {
        var jane = signUp();
        assertThat(save(jane.session(), "classic-invoice")).hasStatus(201);
        assertThat(save(jane.session(), "classic-contract")).hasStatus(201);

        assertThat(save(jane.session(), "classic-unordered"))
                .hasStatus(403)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"You've reached your saved template limit.\"}");
        assertThat(save(jane.session(), "classic-invoice")).hasStatus(200);
        assertThat(FirestoreCalls.await(library(jane.uid()).get()).size()).isEqualTo(2);
    }

    @Test
    void twoSavesAtOnceCantBothPassTheLimit() throws Exception {
        var jane = signUp();
        assertThat(save(jane.session(), "classic-invoice")).hasStatus(201);

        List<Callable<Integer>> saves = List.of(
                () -> save(jane.session(), "classic-contract").getResponse().getStatus(),
                () -> save(jane.session(), "classic-unordered").getResponse().getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (var answer : threads.invokeAll(saves)) {
                statuses.add(answer.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 403);
        assertThat(FirestoreCalls.await(library(jane.uid()).get()).size()).isEqualTo(2);
    }

    @Test
    void thePlanDecidesWhichTemplatesMayBeSaved() {
        var jane = signUp();
        assertThat(save(jane.session(), "modern-invoice"))
                .hasStatus(403)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Your plan does not include this template.\"}");

        // An upgrade counts straight away, without signing in again.
        FirestoreCalls.await(
                firestore.collection("users").document(jane.uid()).set(Map.of("role", "silver"), SetOptions.merge()));

        assertThat(save(jane.session(), "modern-invoice")).hasStatus(201);
        assertThat(save(jane.session(), "brutalist-invoice")).hasStatus(403);
        assertThat(save(jane.session(), "classic-invoice")).hasStatus(201);
        assertThat(save(jane.session(), "classic-contract")).hasStatus(201);
    }

    @Test
    void onlyATemplateOfTheCatalogCanBeSaved() {
        Cookie session = signUp().session();

        for (String id : List.of("classic-retired", "minimalist-invoice", "classic-nope", "a/b")) {
            assertThat(save(session, id))
                    .as(id)
                    .hasStatus(404)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Template not found.\"}");
        }
        for (String body : List.of("{}", "{\"templateId\":\"\"}", "{\"templateId\":5}", "{\"templateId\":null}")) {
            assertThat(call(HttpMethod.POST, "/api/saved-templates", body, session))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
        }
    }

    // The library

    @Test
    void theLibraryListsTheUsersOwnInTemplateIdOrderWithoutRetiredTemplates() {
        var jane = signUp();
        var eve = signUp();
        write("users/" + jane.uid() + "/savedTemplates/classic-invoice", Map.of("savedAt", 2L));
        write("users/" + jane.uid() + "/savedTemplates/classic-contract", Map.of("savedAt", 1L));
        write("users/" + jane.uid() + "/savedTemplates/classic-retired", Map.of("savedAt", 3L));
        write("users/" + jane.uid() + "/savedTemplates/classic-unordered", Map.of("savedAt", "soon"));
        write("users/" + eve.uid() + "/savedTemplates/modern-invoice", Map.of("savedAt", 4L));

        JsonNode library = body(call(HttpMethod.GET, "/api/saved-templates", null, jane.session()));

        var entries = library.path("savedTemplates");
        assertThat(entries.valueStream().map(entry -> entry.path("templateId").asString()))
                .containsExactly("classic-contract", "classic-invoice", "classic-unordered");
        assertThat(entries.get(0).path("savedAt").asLong()).isEqualTo(1);
        assertThat(entries.get(0).path("template").path("id").asString()).isEqualTo("classic-contract");
        assertThat(entries.get(2).path("savedAt").asLong()).isZero();
        assertThat(call(HttpMethod.GET, "/api/saved-templates", null, signUp().session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"savedTemplates\":[]}");
    }

    @Test
    void removingIsQuietAndOnlyAffectsTheUsersOwn() {
        var jane = signUp();
        var eve = signUp();
        save(jane.session(), "classic-invoice");
        save(eve.session(), "classic-invoice");

        assertThat(call(HttpMethod.DELETE, "/api/saved-templates/classic-invoice", null, jane.session()))
                .hasStatus(204);
        assertThat(call(HttpMethod.DELETE, "/api/saved-templates/classic-invoice", null, jane.session()))
                .hasStatus(204);
        assertThat(call(HttpMethod.DELETE, "/api/saved-templates/__name__", null, jane.session()))
                .hasStatus(204);

        assertThat(FirestoreCalls.await(savedInvoice(jane.uid()).get()).exists())
                .isFalse();
        assertThat(FirestoreCalls.await(savedInvoice(eve.uid()).get()).exists()).isTrue();
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

    private MvcTestResult save(Cookie session, String templateId) {
        return call(HttpMethod.POST, "/api/saved-templates", "{\"templateId\":\"" + templateId + "\"}", session);
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

    private static List<String> ids(JsonNode list) {
        return list.valueStream().map(entry -> entry.path("id").asString()).toList();
    }

    private void style(String id, String tier, long sortOrder, boolean active) {
        write(
                "templateStyles/" + id,
                Map.of(
                        "name", "Name of " + id,
                        "description", "About " + id,
                        "tier", tier,
                        "sortOrder", sortOrder,
                        "isActive", active));
    }

    private static Map<String, Object> template(
            String id, String styleId, String tier, @Nullable Long sortOrder, boolean active) {
        var fields = new HashMap<String, Object>();
        fields.put("name", "Name of " + id);
        fields.put("description", "About " + id);
        fields.put("documentType", "invoice");
        fields.put("styleId", styleId);
        fields.put("tier", tier);
        fields.put("fields", List.of());
        fields.put("isActive", active);
        if (sortOrder != null) {
            fields.put("sortOrder", sortOrder);
        }
        return fields;
    }

    private void write(String path, Map<String, Object> fields) {
        FirestoreCalls.await(firestore.document(path).set(fields));
    }

    private com.google.cloud.firestore.CollectionReference library(String uid) {
        return firestore.collection("users").document(uid).collection("savedTemplates");
    }

    /** The saved invoice template, the one the tests save and look for. */
    private DocumentReference savedInvoice(String uid) {
        return library(uid).document("classic-invoice");
    }
}

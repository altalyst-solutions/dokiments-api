package com.dokiments.sharing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.FirebaseEmulatorsContainer;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
 * Sharing documents end to end, as the web app does it: through the real security setup, with sessions in Redis,
 * accounts in the Auth emulator, and documents, shared copies and ledgers in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SharingIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final String SIGNABLE = "sharing-it-contract";
    private static final String PLAIN = "sharing-it-letter";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirebaseEmulatorsContainer emulators;

    @BeforeEach
    void writeTheTemplates() {
        write(
                "templateStyles/classic",
                Map.of("name", "Classic", "description", "", "tier", "free", "sortOrder", 0L, "isActive", true));
        write("templates/" + SIGNABLE, template("Contract", "recipientSignature"));
        write("templates/" + PLAIN, template("Letter", "senderSignature"));
    }

    /** The catalog is shared by every test class, so these entries don't stay in it. */
    @AfterEach
    void removeTheTemplates() {
        for (String id : List.of(SIGNABLE, PLAIN)) {
            FirestoreCalls.await(firestore.document("templates/" + id).delete());
        }
    }

    @Test
    void everyRouteNeedsASession() {
        for (var call : List.of(
                call(HttpMethod.POST, "/api/documents/doc_x/share", "{\"email\":\"a@b.co\"}", null),
                call(HttpMethod.GET, "/api/documents/share-recipients", null, null),
                call(HttpMethod.GET, "/api/documents/shared", null, null),
                call(HttpMethod.POST, "/api/documents/shared/x_doc_x/revoke", null, null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    // Sharing

    @Test
    void aShareMakesACopyAndALedgerEntry() {
        var jane = signUp("Jane Doe");
        String doc = create(jane, SIGNABLE);

        MvcTestResult shared = share(jane, doc, " Client@Acme.example ");

        String copyId = jane.uid() + "_" + doc;
        assertThat(shared)
                .hasStatus(201)
                .bodyJson()
                .isStrictlyEqualTo("{\"recipient\":{\"email\":\"client@acme.example\",\"status\":\"pending\"},"
                        + "\"sharedDocumentId\":\"" + copyId + "\"}");

        var copy = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(copy.getString("ownerId")).isEqualTo(jane.uid());
        assertThat(copy.getString("ownerName")).isEqualTo("Jane Doe");
        assertThat(copy.getString("ownerEmail")).isEqualTo(jane.email());
        assertThat(copy.getBoolean("locked")).isFalse();
        assertThat(copy.get("recipientEmails")).isEqualTo(List.of("client@acme.example"));
        assertThat(copy.get("recipientUids")).isEqualTo(List.of());
        assertThat(copy.get("values")).isEqualTo(Map.of("client", "Acme"));
        assertThat(copy.get("signatures")).isEqualTo(Map.of());
        assertThat(copy.contains("views")).isFalse();
        assertThat(copy.get("templateSnapshot.name")).isEqualTo("Contract");
        assertThat(copy.getString("sourceDocumentId")).isEqualTo(doc);

        var entry = FirestoreCalls.await(clientEntry(jane.uid()).get());
        assertThat(entry.getString("email")).isEqualTo("client@acme.example");
        assertThat(entry.getTimestamp("firstSharedAt")).isEqualTo(entry.getTimestamp("lastSharedAt"));
    }

    @Test
    void sharingAgainWithThePersonWhoHasItRefreshesTheCopy() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        share(jane, doc, "client@acme.example");
        var first = FirestoreCalls.await(clientEntry(jane.uid()).get());

        assertThat(share(jane, doc, "CLIENT@acme.example")).hasStatus(200);

        var again = FirestoreCalls.await(clientEntry(jane.uid()).get());
        assertThat(again.getTimestamp("firstSharedAt")).isEqualTo(first.getTimestamp("firstSharedAt"));
        assertThat(Objects.requireNonNull(again.getTimestamp("lastSharedAt")))
                .isGreaterThanOrEqualTo(Objects.requireNonNull(first.getTimestamp("lastSharedAt")));
    }

    @Test
    void aShareIsRefusedForTheWebAppsReasons() {
        var jane = signUp("Jane");
        String signable = create(jane, SIGNABLE);
        String plain = create(jane, PLAIN);
        share(jane, signable, "client@acme.example");
        var eve = signUp("Eve");

        assertError(
                share(jane, signable, "other@acme.example"),
                409,
                "This document is already shared with someone else. Sharing with more than one person is not"
                        + " available yet.");
        // Checked after "shared with someone else", as in the web app, so on a document not shared yet.
        assertError(
                share(jane, create(jane, SIGNABLE), jane.email()), 400, "You cannot share a document with yourself.");
        assertError(
                share(jane, plain, "client@acme.example"),
                400,
                "This type of document has no recipient signature, so it cannot be shared for signing.");
        assertError(share(eve, signable, "client@acme.example"), 404, "Document not found.");
        assertError(share(jane, "doc_missing", "client@acme.example"), 404, "Document not found.");
        for (String body : List.of("{}", "{\"email\":\"nope\"}", "{\"email\":5}")) {
            assertError(
                    call(HttpMethod.POST, "/api/documents/" + plain + "/share", body, jane.session()),
                    400,
                    "Invalid request body.");
        }
    }

    @Test
    void twoSharesWithDifferentPeopleAtOnceCantBothSucceed() throws Exception {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);

        List<Callable<Integer>> shares = List.of(
                () -> share(jane, doc, "first@acme.example").getResponse().getStatus(),
                () -> share(jane, doc, "second@acme.example").getResponse().getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (var answer : threads.invokeAll(shares)) {
                statuses.add(answer.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        var copy = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + jane.uid() + "_" + doc).get());
        assertThat((List<?>) copy.get("recipientEmails")).hasSize(1);
    }

    @Test
    void aSignedCopyCantBeSharedOrTakenBack() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        share(jane, doc, "client@acme.example");
        String copyId = jane.uid() + "_" + doc;
        FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).set(Map.of("locked", true), SetOptions.merge()));

        String locked = "This document has been signed and can no longer be changed.";
        assertError(share(jane, doc, "client@acme.example"), 409, locked);
        assertError(revoke(jane, copyId), 409, locked);
    }

    // The ledger and the lists

    @Test
    void theLedgerListsEveryoneSharedWithMostRecentFirst() {
        var jane = signUp("Jane");
        share(jane, create(jane, SIGNABLE), "first@acme.example");
        share(jane, create(jane, SIGNABLE), "second@acme.example");

        JsonNode recipients = body(call(HttpMethod.GET, "/api/documents/share-recipients", null, jane.session()))
                .path("recipients");

        assertThat(recipients.valueStream().map(entry -> entry.path("email").asString()))
                .containsExactly("second@acme.example", "first@acme.example");
        assertThat(recipients.get(0).path("firstSharedAt").asLong()).isPositive();
    }

    @Test
    void theOwnerSeesTheirCopiesAndAVerifiedRecipientSeesWhatWasSharedWithThem() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());

        JsonNode byMe = body(call(HttpMethod.GET, "/api/documents/shared?by=me", null, jane.session()))
                .path("documents");
        assertThat(byMe).hasSize(1);
        JsonNode copy = byMe.get(0);
        assertThat(copy.path("id").asString()).isEqualTo(jane.uid() + "_" + doc);
        assertThat(copy.path("recipients").get(0).path("status").asString()).isEqualTo("pending");
        assertThat(copy.path("recipients").get(0).has("emailedAt")).isFalse();
        assertThat(copy.path("views").isObject()).isTrue();
        assertThat(copy.path("values").path("client").asString()).isEqualTo("Acme");
        assertThat(copy.path("templateSnapshot")
                        .path("fields")
                        .get(1)
                        .path("key")
                        .asString())
                .isEqualTo("recipientSignature");

        // Until the address is verified, an account under it sees nothing shared with it.
        assertThat(sharedWith(client.session())).isEmpty();
        Cookie verified = verifyAndSignIn(client);
        assertThat(sharedWith(verified).stream()
                        .map(document -> document.path("id").asString()))
                .containsExactly(jane.uid() + "_" + doc);
        assertThat(sharedWith(jane.session())).isEmpty();
    }

    // Taking it back

    @Test
    void takingACopyBackEndsTheRecipientsAccessAndSharingAgainGivesItBack() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;

        MvcTestResult revoked = revoke(jane, copyId);

        assertThat(revoked).hasStatusOk();
        JsonNode recipient = body(revoked).path("document").path("recipients").get(0);
        assertThat(recipient.path("status").asString()).isEqualTo("revoked");
        assertThat(recipient.path("revokedAt").asLong()).isPositive();
        var stored = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(stored.get("recipientEmails")).isEqualTo(List.of());
        assertThat(sharedWith(verified)).isEmpty();
        assertError(revoke(jane, copyId), 409, "This document is not shared with anyone.");

        assertThat(share(jane, doc, client.email())).hasStatus(201);
        assertThat(sharedWith(verified)).hasSize(1);
    }

    @Test
    void onlyTheOwnerCanTakeACopyBack() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        share(jane, doc, "client@acme.example");
        var eve = signUp("Eve");

        for (String id : List.of(jane.uid() + "_" + doc, "missing_doc", "__name__")) {
            assertError(revoke(eve, id), 404, "Document not found.");
        }
        assertThat(FirestoreCalls.await(firestore
                                .document("sharedDocuments/" + jane.uid() + "_" + doc)
                                .get())
                        .get("recipientEmails"))
                .isEqualTo(List.of("client@acme.example"));
    }

    // Helpers

    private static final String PASSWORD = "secret123";

    private record Account(String uid, String email, Cookie session) {}

    private Account signUp(String name) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult result = call(
                HttpMethod.POST,
                "/api/auth/sign-up",
                "{\"displayName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}",
                null);
        assertThat(result).hasStatusOk();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return new Account(body(result).path("user").path("uid").asString(), email, session);
    }

    /** Marks the address verified in the Auth emulator, and signs in again, so the session knows. */
    private Cookie verifyAndSignIn(Account account) {
        markVerified(account.uid());
        MvcTestResult signedIn = call(
                HttpMethod.POST,
                "/api/auth/sign-in",
                "{\"email\":\"" + account.email() + "\",\"password\":\"" + PASSWORD + "\"}",
                null);
        assertThat(signedIn).hasStatusOk();
        assertThat(body(signedIn).path("user").path("emailVerified").asBoolean())
                .isTrue();
        return Objects.requireNonNull(signedIn.getResponse().getCookie(SESSION_COOKIE));
    }

    private String create(Account owner, String templateId) {
        MvcTestResult created = call(
                HttpMethod.POST,
                "/api/documents",
                "{\"name\":\"For Acme\",\"templateId\":\"" + templateId + "\",\"values\":{\"client\":\"Acme\","
                        + "\"recipientSignature\":\"forged\"}}",
                owner.session());
        assertThat(created).hasStatus(201);
        return body(created).path("document").path("id").asString();
    }

    private MvcTestResult share(Account owner, String documentId, String email) {
        return call(
                HttpMethod.POST,
                "/api/documents/" + documentId + "/share",
                "{\"email\":\"" + email + "\"}",
                owner.session());
    }

    private MvcTestResult revoke(Account owner, String sharedDocumentId) {
        return call(HttpMethod.POST, "/api/documents/shared/" + sharedDocumentId + "/revoke", null, owner.session());
    }

    private List<JsonNode> sharedWith(Cookie session) {
        return body(call(HttpMethod.GET, "/api/documents/shared", null, session))
                .path("documents")
                .valueStream()
                .toList();
    }

    private static void assertError(MvcTestResult result, int status, String message) {
        assertThat(result)
                .hasStatus(status)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":" + JSON.writeValueAsString(message) + "}");
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

    private static Map<String, Object> template(String name, String signatureKey) {
        return Map.of(
                "name",
                name,
                "description",
                "",
                "documentType",
                "contract",
                "styleId",
                "classic",
                "tier",
                "free",
                "sortOrder",
                200L,
                "isActive",
                true,
                "fields",
                List.of(
                        Map.of("key", "client", "label", "Client", "type", "text"),
                        Map.of("key", signatureKey, "label", "Signature", "type", "text")));
    }

    private void write(String path, Map<String, Object> fields) {
        FirestoreCalls.await(firestore.document(path).set(fields));
    }

    /** The owner's ledger entry for the client the tests share with. */
    private DocumentReference clientEntry(String uid) {
        return firestore
                .collection("users")
                .document(uid)
                .collection("shareRecipients")
                .document("client@acme.example");
    }

    /** Marks an address verified, with the Auth emulator's administrator API, which only the emulator offers. */
    // The Auth emulator only speaks plain HTTP, inside the test container.
    @SuppressWarnings("HttpUrlsUsage")
    private void markVerified(String uid) {
        String path = "/identitytoolkit.googleapis.com/v1/projects/demo-dokiments/accounts:update";
        String body = "{\"localId\":\"" + uid + "\",\"emailVerified\":true}";
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://" + emulators.authEndpoint() + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer owner")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

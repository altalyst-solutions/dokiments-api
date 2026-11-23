package com.dokiments.sharing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dokiments.platform.email.EmailMessage;
import com.dokiments.platform.email.EmailResult;
import com.dokiments.platform.email.EmailSender;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.sharing.domain.InviteToken;
import com.dokiments.support.FirebaseEmulatorsContainer;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    // Email is sent after the answer, so the tests look at what was handed to the sender, which never sends anything.
    @MockitoBean
    private EmailSender emailSender;

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
                call(HttpMethod.POST, "/api/documents/shared/x_doc_x/revoke", null, null),
                call(HttpMethod.GET, "/api/documents/shared/x_doc_x", null, null),
                call(HttpMethod.POST, "/api/documents/shared/x_doc_x/sign", "{\"signature\":\"x\"}", null))) {
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

    // Sharing to view

    @Test
    void aDocumentWithoutARecipientSignatureIsSharedToViewAndCannotBeSigned() {
        var jane = signUp("Jane");
        String doc = create(jane, PLAIN);
        var client = signUp("Client");

        assertThat(share(jane, doc, client.email())).hasStatus(201);

        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;
        assertThat(open(verified, copyId)).hasStatusOk();
        assertError(
                sign(verified, copyId, "Client"),
                400,
                "This document has no recipient signature. It was shared to view, so it cannot be signed.");
        assertThat(FirestoreCalls.await(
                                firestore.document("sharedDocuments/" + copyId).get())
                        .getBoolean("locked"))
                .isFalse();
        verify(emailSender, after(500).never()).send(argThat(SharingIT::isSignedEmail));
    }

    // Opening

    @Test
    void theOwnerAndAVerifiedRecipientOpenACopyAndNobodyElseCan() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        String copyId = jane.uid() + "_" + doc;
        var stranger = verifyAndSignIn(signUp("Stranger"));
        String denied = "You do not have access to this shared document.";

        assertThat(body(open(jane.session(), copyId))
                        .path("document")
                        .path("id")
                        .asString())
                .isEqualTo(copyId);
        assertError(open(client.session(), copyId), 404, denied);
        Cookie verified = verifyAndSignIn(client);
        JsonNode copy = body(open(verified, copyId)).path("document");
        assertThat(copy.path("ownerName").asString()).isEqualTo("Jane");
        assertThat(copy.path("recipients").get(0).path("email").asString()).isEqualTo(client.email());
        assertThat(copy.path("values").path("client").asString()).isEqualTo("Acme");
        assertError(open(stranger, copyId), 404, denied);
        assertError(open(verified, "missing_doc"), 404, denied);
        assertError(open(verified, "__name__"), 404, denied);
        assertThat(call(HttpMethod.GET, "/api/documents/shared/" + copyId, null, null))
                .hasStatus(401);
    }

    @Test
    void aRecipientsFirstOpeningIsRecordedOnceWithoutChangingTheCopy() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;
        var before = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());

        JsonNode firstAnswer = body(open(verified, copyId)).path("document");
        assertThat(firstAnswer.path("views").isObject()).isTrue();
        assertThat(firstAnswer.path("views")).isEmpty();

        var after = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(after.get(FieldPath.of("views", client.uid(), "email"))).isEqualTo(client.email());
        assertThat(after.getTimestamp("updatedAt")).isEqualTo(before.getTimestamp("updatedAt"));
        var viewedAt = after.get(FieldPath.of("views", client.uid(), "viewedAt"));

        open(verified, copyId);
        open(jane.session(), copyId);
        var again = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(again.get(FieldPath.of("views", client.uid(), "viewedAt"))).isEqualTo(viewedAt);
        assertThat(((Map<?, ?>) Objects.requireNonNull(again.get("views")))).hasSize(1);

        JsonNode owners = body(call(HttpMethod.GET, "/api/documents/shared?by=me", null, jane.session()))
                .path("documents")
                .get(0);
        assertThat(owners.path("recipients").get(0).path("status").asString()).isEqualTo("viewed");
        assertThat(owners.path("views").path(client.uid()).path("email").asString())
                .isEqualTo(client.email());
    }

    @Test
    void theOwnerOpeningTheirCopyRecordsNoView() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        share(jane, doc, "client@acme.example");

        open(jane.session(), jane.uid() + "_" + doc);

        assertThat(FirestoreCalls.await(firestore
                                .document("sharedDocuments/" + jane.uid() + "_" + doc)
                                .get())
                        .contains("views"))
                .isFalse();
    }

    @Test
    void anAccountAnInviteLinkedOpensAndSignsWithoutAVerifiedAddressAndSharingAgainKeepsIt() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        String copyId = jane.uid() + "_" + doc;
        // What accepting an invitation link writes: the account's ID, on the copy's list of accounts with access.
        FirestoreCalls.await(firestore
                .document("sharedDocuments/" + copyId)
                .update("recipientUids", FieldValue.arrayUnion(client.uid())));

        assertThat(sharedWith(client.session())).hasSize(1);
        assertThat(open(client.session(), copyId)).hasStatusOk();
        assertThat(share(jane, doc, client.email())).hasStatus(200);
        assertThat(FirestoreCalls.await(
                                firestore.document("sharedDocuments/" + copyId).get())
                        .get("recipientUids"))
                .isEqualTo(List.of(client.uid()));
        assertThat(sign(client.session(), copyId, "Client")).hasStatusOk();
    }

    // Signing

    @Test
    void aRecipientSignsAndTheCopyIsLockedAndTheOwnerIsEmailed() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;
        var senders = new ConcurrentLinkedQueue<Thread>();
        doAnswer(call -> {
                    if (isSignedEmail(call.getArgument(0))) {
                        senders.add(Thread.currentThread());
                    }
                    return null;
                })
                .when(emailSender)
                .send(any());

        MvcTestResult signed = sign(verified, copyId, "  Client Name ");

        assertThat(signed).hasStatusOk();
        JsonNode copy = body(signed).path("document");
        assertThat(copy.path("locked").asBoolean()).isTrue();
        assertThat(copy.path("signatures").path(client.uid()).path("value").asString())
                .isEqualTo("Client Name");
        assertThat(copy.path("signatures").path(client.uid()).path("email").asString())
                .isEqualTo(client.email());
        assertThat(copy.path("signatures").path(client.uid()).path("type").asString())
                .isEqualTo("typed");
        assertThat(copy.path("recipients").get(0).path("status").asString()).isEqualTo("signed");

        var stored = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(stored.getBoolean("locked")).isTrue();
        assertThat(stored.get(FieldPath.of("signatures", client.uid(), "value")))
                .isEqualTo("Client Name");
        assertThat(stored.get(FieldPath.of("signatures", client.uid(), "signedAt")))
                .isInstanceOf(Timestamp.class);

        // Sharing sends its own email; this test is about the one the signature sends.
        verify(emailSender, timeout(5000)).send(argThat(SharingIT::isSignedEmail));
        var captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(captured.capture());
        EmailMessage email = captured.getAllValues().stream()
                .filter(SharingIT::isSignedEmail)
                .findFirst()
                .orElseThrow();
        assertThat(email.to()).isEqualTo(jane.email());
        assertThat(email.subject()).isEqualTo("Client signed \"For Acme\"");
        assertThat(email.template()).isEqualTo("sharing/document-signed");
        assertThat(email.variables()).containsEntry("signerName", "Client").containsEntry("documentName", "For Acme");
        assertThat((String) email.variables().get("url")).endsWith("/my-documents/shared/" + copyId);
        assertThat(email.idempotencyKey()).startsWith("signed-");
        // The answer doesn't wait for the email: it is sent from another, virtual, thread.
        assertThat(senders).singleElement().satisfies(sender -> {
            assertThat(sender).isNotSameAs(Thread.currentThread());
            assertThat(sender.isVirtual()).isTrue();
        });

        String locked = "This document has been signed and can no longer be changed.";
        assertError(sign(verified, copyId, "Again"), 409, locked);
        assertError(share(jane, doc, client.email()), 409, locked);
        assertError(revoke(jane, copyId), 409, locked);
    }

    @Test
    void aSigningIsRefusedForTheWebAppsReasons() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        String copyId = jane.uid() + "_" + doc;
        Cookie verified = verifyAndSignIn(client);
        String denied = "You do not have access to this shared document.";

        assertError(sign(jane.session(), copyId, "Jane"), 404, denied);
        assertError(sign(client.session(), copyId, "Client"), 404, denied);
        assertError(sign(verifyAndSignIn(signUp("Stranger")), copyId, "Stranger"), 404, denied);
        assertError(sign(verified, "missing_doc", "Client"), 404, denied);
        for (String body : List.of(
                "{}",
                "{\"signature\":\"\"}",
                "{\"signature\":\"   \"}",
                "{\"signature\":5}",
                "{\"signature\":null}",
                "{\"signature\":\"" + "x".repeat(201) + "\"}")) {
            assertError(
                    call(HttpMethod.POST, "/api/documents/shared/" + copyId + "/sign", body, verified),
                    400,
                    "Invalid request body.");
        }
        assertThat(sign(verified, copyId, "x".repeat(200))).hasStatusOk();
    }

    @Test
    void aRecipientTakenBackFromCannotOpenOrSign() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;
        revoke(jane, copyId);

        String denied = "You do not have access to this shared document.";
        assertError(open(verified, copyId), 404, denied);
        assertError(sign(verified, copyId, "Client"), 404, denied);
        verify(emailSender, after(500).never()).send(argThat(SharingIT::isSignedEmail));
    }

    @Test
    void twoSignaturesAtOnceCantBothSucceed() throws Exception {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;

        List<Callable<Integer>> signatures = List.of(
                () -> sign(verified, copyId, "First").getResponse().getStatus(),
                () -> sign(verified, copyId, "Second").getResponse().getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (var answer : threads.invokeAll(signatures)) {
                statuses.add(answer.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        var stored = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat((Map<?, ?>) Objects.requireNonNull(stored.get("signatures"))).hasSize(1);
        verify(emailSender, timeout(5000).times(1)).send(argThat(SharingIT::isSignedEmail));
    }

    // Notifications

    @Test
    void theRecipientIsNotifiedOfTheShareAndTheOwnerOfTheSignature() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        share(jane, doc, client.email());
        Cookie verified = verifyAndSignIn(client);
        String copyId = jane.uid() + "_" + doc;

        JsonNode shared = notificationsOf(verified, "document-shared").getFirst();
        assertThat(shared.path("id").asString()).isEqualTo("document-shared:" + copyId);
        assertThat(shared.path("title").asString()).isEqualTo("Jane shared a document");
        assertThat(shared.path("body").asString()).isEqualTo("For Acme");
        assertThat(shared.path("action").path("href").asString()).isEqualTo("/my-documents?tab=shared");
        assertThat(notificationsOf(jane.session(), "document-shared")).isEmpty();
        assertThat(notificationsOf(jane.session(), "document-signed")).isEmpty();

        sign(verified, copyId, "Client Name");

        JsonNode signed = notificationsOf(jane.session(), "document-signed").getFirst();
        assertThat(signed.path("id").asString()).isEqualTo("document-signed:" + copyId + ":" + client.uid());
        assertThat(signed.path("title").asString()).isEqualTo(client.email() + " signed a document");
        assertThat(signed.path("action").path("href").asString()).isEqualTo("/my-documents/shared/" + copyId);
        assertThat(notificationsOf(client.session(), "document-shared")).isEmpty();
    }

    // The share email and its invitation

    @Test
    void theShareEmailCarriesAnInvitationLinkThatWorksForFourteenDays() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");

        EmailMessage email = shareAndCaptureEmail(jane, doc, client.email());

        String copyId = jane.uid() + "_" + doc;
        assertThat(email.to()).isEqualTo(client.email());
        assertThat(email.subject()).isEqualTo("Jane shared \"For Acme\" with you");
        assertThat(email.variables())
                .containsEntry("updated", false)
                .containsEntry("signable", true)
                .containsEntry("senderName", "Jane")
                .containsEntry("documentName", "For Acme");
        assertThat(email.idempotencyKey()).startsWith("share-");
        String token = tokenIn(email);

        var invite = FirestoreCalls.await(
                firestore.document("sharedInvites/" + InviteToken.hash(token)).get());
        assertThat(invite.getString("email")).isEqualTo(client.email());
        assertThat(invite.getString("ownerId")).isEqualTo(jane.uid());
        assertThat(invite.getString("sharedDocumentId")).isEqualTo(copyId);
        assertThat(invite.getString("name")).isEqualTo("For Acme");
        var created = Objects.requireNonNull(invite.getTimestamp("createdAt"))
                .toDate()
                .toInstant();
        assertThat(Objects.requireNonNull(invite.getTimestamp("expiresAt"))
                        .toDate()
                        .toInstant())
                .isEqualTo(created.plus(Duration.ofDays(14)));
        awaitTrue(() -> emailedAt(copyId).size() == 1);
    }

    @Test
    void sharingAgainWithinTenMinutesSendsNoSecondEmail() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        shareAndCaptureEmail(jane, doc, client.email());
        awaitTrue(() -> emailedAt(jane.uid() + "_" + doc).size() == 1);

        assertThat(share(jane, doc, client.email())).hasStatus(200);

        verify(emailSender, after(1000).times(1)).send(argThat(SharingIT::isShareEmail));
    }

    @Test
    void anEmailThatCouldntBeSentLeavesNoInvitation() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        when(emailSender.send(any())).thenReturn(new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR));

        share(jane, doc, "client@acme.example");

        verify(emailSender, timeout(5000)).send(argThat(SharingIT::isShareEmail));
        String copyId = jane.uid() + "_" + doc;
        awaitTrue(() -> invitesFor(copyId) == 0);
        assertThat(emailedAt(copyId)).isEmpty();
    }

    // Invitations

    @Test
    void anInvitationLinkShowsItsPreviewToAnyoneAndIsNeverCached() {
        var jane = signUp("Jane");
        String token = tokenIn(shareAndCaptureEmail(jane, create(jane, SIGNABLE), "client@acme.example"));

        MvcTestResult preview = mvc.get().uri("/api/shared-invites/" + token).exchange();

        assertThat(preview)
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"invite\":{\"name\":\"For Acme\",\"ownerEmail\":\"" + jane.email()
                        + "\",\"ownerName\":\"Jane\",\"signable\":true},\"state\":\"valid\"}");
        assertThat(preview.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        for (String unknown : List.of("x".repeat(43), "not-a-token")) {
            assertThat(mvc.get().uri("/api/shared-invites/" + unknown).exchange())
                    .as(unknown)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"state\":\"invalid\"}");
        }

        expire(token);
        assertThat(mvc.get().uri("/api/shared-invites/" + token).exchange())
                .bodyJson()
                .isStrictlyEqualTo("{\"state\":\"expired\"}");
    }

    @Test
    void acceptingLinksTheInvitedAccountWhichCanThenOpenTheCopyWithoutAVerifiedAddress() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        String token = tokenIn(shareAndCaptureEmail(jane, doc, client.email()));
        String copyId = jane.uid() + "_" + doc;
        assertThat(open(client.session(), copyId)).hasStatus(404);

        assertThat(accept(client.session(), token))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"sharedDocumentId\":\"" + copyId + "\"}");

        var stored = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + copyId).get());
        assertThat(stored.get("recipientUids")).isEqualTo(List.of(client.uid()));
        assertThat(stored.getString("acceptedInvite")).isEqualTo(InviteToken.hash(token));
        assertThat(open(client.session(), copyId)).hasStatusOk();
        assertThat(accept(client.session(), token)).hasStatusOk();
    }

    @Test
    void anInvitationIsRefusedForTheWebAppsReasons() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        String token = tokenIn(shareAndCaptureEmail(jane, doc, client.email()));
        var eve = signUp("Eve");

        assertThat(accept(eve.session(), token))
                .hasStatus(403)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"This invitation was sent to a different email address.\","
                        + "\"invitedEmail\":\"u***@example.com\"}");
        assertError(accept(client.session(), "x".repeat(43)), 404, "This invitation is no longer valid.");
        assertThat(call(HttpMethod.POST, "/api/shared-invites/" + token + "/accept", null, null))
                .hasStatus(401);

        expire(token);
        assertError(accept(client.session(), token), 410, "This invitation has expired.");
    }

    @Test
    void takingTheCopyBackEndsItsInvitations() {
        var jane = signUp("Jane");
        String doc = create(jane, SIGNABLE);
        var client = signUp("Client");
        String token = tokenIn(shareAndCaptureEmail(jane, doc, client.email()));
        String copyId = jane.uid() + "_" + doc;

        assertThat(revoke(jane, copyId)).hasStatusOk();

        assertThat(invitesFor(copyId)).isZero();
        assertError(accept(client.session(), token), 404, "This invitation is no longer valid.");
        assertThat(mvc.get().uri("/api/shared-invites/" + token).exchange())
                .bodyJson()
                .isStrictlyEqualTo("{\"state\":\"invalid\"}");
    }

    // Helpers

    /** The email sharing sends to the person shared with. */
    private static boolean isShareEmail(@Nullable EmailMessage message) {
        return message != null && message.template().equals("sharing/document-shared");
    }

    /** Shares, with emails going out, and returns the email the recipient got. */
    private EmailMessage shareAndCaptureEmail(Account owner, String documentId, String email) {
        when(emailSender.send(any())).thenReturn(new EmailResult.Sent("email-id"));
        assertThat(share(owner, documentId, email).getResponse().getStatus()).isIn(200, 201);
        verify(emailSender, timeout(5000))
                .send(argThat(message -> isShareEmail(message) && message.to().equals(email)));
        var captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(captured.capture());
        return captured.getAllValues().stream()
                .filter(message -> isShareEmail(message) && message.to().equals(email))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    /** The invitation token in a share email's link. */
    private static String tokenIn(EmailMessage email) {
        String url = (String) Objects.requireNonNull(email.variables().get("url"));
        assertThat(url).matches(".*/shared/[A-Za-z0-9_-]{43}");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MvcTestResult accept(Cookie session, String token) {
        return call(HttpMethod.POST, "/api/shared-invites/" + token + "/accept", null, session);
    }

    /** Moves an invitation's end into the past. */
    private void expire(String token) {
        FirestoreCalls.await(firestore
                .document("sharedInvites/" + InviteToken.hash(token))
                .update("expiresAt", FirestoreCalls.toTimestamp(Instant.now().minusSeconds(1))));
    }

    private int invitesFor(String sharedDocumentId) {
        return FirestoreCalls.await(firestore
                        .collection("sharedInvites")
                        .whereEqualTo("sharedDocumentId", sharedDocumentId)
                        .get())
                .size();
    }

    private List<?> emailedAt(String sharedDocumentId) {
        var stored = FirestoreCalls.await(
                firestore.document("sharedDocuments/" + sharedDocumentId).get());
        var recipients = (List<?>) Objects.requireNonNull(stored.get("recipients"));
        var first = (Map<?, ?>) recipients.getFirst();
        return first.get("emailedAt") instanceof List<?> times ? times : List.of();
    }

    /** Waits for what the email listener does after the answer, for up to five seconds. */
    private static void awaitTrue(Callable<Boolean> condition) {
        await().atMost(Duration.ofSeconds(5)).until(condition);
    }

    /** The email the owner gets when their copy is signed, as opposed to the one sharing sends. */
    private static boolean isSignedEmail(@Nullable EmailMessage message) {
        return message != null && message.template().equals("sharing/document-signed");
    }

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

    private MvcTestResult open(Cookie session, String sharedDocumentId) {
        return call(HttpMethod.GET, "/api/documents/shared/" + sharedDocumentId, null, session);
    }

    private MvcTestResult sign(Cookie session, String sharedDocumentId, String signature) {
        return call(
                HttpMethod.POST,
                "/api/documents/shared/" + sharedDocumentId + "/sign",
                "{\"signature\":" + JSON.writeValueAsString(signature) + "}",
                session);
    }

    private List<JsonNode> notificationsOf(Cookie session, String kind) {
        return body(call(HttpMethod.GET, "/api/notifications", null, session))
                .path("notifications")
                .valueStream()
                .filter(notification -> kind.equals(notification.path("kind").asString()))
                .toList();
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

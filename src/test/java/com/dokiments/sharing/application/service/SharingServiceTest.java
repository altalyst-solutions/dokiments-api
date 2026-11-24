package com.dokiments.sharing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.documents.Documents;
import com.dokiments.documents.OwnedDocument;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.shared.error.UpstreamUnavailableException;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.DocumentSigned;
import com.dokiments.sharing.domain.InvalidShareException;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.NotRecipientException;
import com.dokiments.sharing.domain.Recipient;
import com.dokiments.sharing.domain.RecipientStatus;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Viewer;
import com.dokiments.support.TestUser;
import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserProfiles;
import com.dokiments.users.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@NullMarked
class SharingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00.123456Z");
    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-11-16T09:00:00.123Z");
    private static final TemplateSnapshot CONTRACT = contractTemplate();
    private static final TestUser CLIENT = new TestUser("uid-2", "Client@Acme.example", "Client", true);

    private final List<SharedDocumentStore.ShareRequest> shared = new ArrayList<>();
    private final List<SharedDocument> owned = new ArrayList<>();
    private final List<Viewer> queriedBy = new ArrayList<>();
    private final List<Viewer> noted = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private final InMemoryInvites invites = new InMemoryInvites();
    private final Map<String, SharedDocument> copies = new HashMap<>();
    private boolean notingFails;
    private final Map<String, OwnedDocument> documentsById = Map.of(
            "doc_1", new OwnedDocument("doc_1", "Contract", "classic-contract", Map.of(), CONTRACT),
            "doc_plain", new OwnedDocument("doc_plain", "Letter", "classic-letter", Map.of(), null));

    @Test
    void whileSharingIsOffEveryRouteIsNotFound() {
        var service = service(false);

        for (ThrowingCallable call : List.<ThrowingCallable>of(
                () -> service.share(TestUser.JANE, "doc_1", "client@acme.example"),
                () -> service.ledger(TestUser.JANE),
                () -> service.sharedBy(TestUser.JANE),
                () -> service.sharedWith(TestUser.JANE),
                () -> service.open(TestUser.JANE, "uid-1_doc_1"),
                () -> service.sign(TestUser.JANE, "uid-1_doc_1", "Jane"),
                () -> service.revoke(TestUser.JANE, "uid-1_doc_1"))) {
            assertThatThrownBy(call).isInstanceOf(NotFoundException.class).hasMessage("Not found.");
        }
        assertThat(shared).isEmpty();
    }

    @Test
    void aShareGoesToTheStoreWithTheCleanedAddressAndTheTimeInMilliseconds() {
        service(true).share(TestUser.JANE, "doc_1", " Client@Acme.example ");

        var request = shared.getFirst();
        assertThat(request.email()).isEqualTo("client@acme.example");
        assertThat(request.ownerUid()).isEqualTo("uid-1");
        assertThat(request.ownerName()).isEqualTo("Jane");
        assertThat(request.now()).isEqualTo(NOW_IN_MILLIS);
        assertThat(request.limit()).isNull();
    }

    @Test
    void onlyTheOwnersDocumentsCanBeSharedAndAnyOfThemCanBeSharedToView() {
        var service = service(true);

        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_missing", "client@acme.example"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Document not found.");
        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_1", null)).isInstanceOf(InvalidShareException.class);
        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_1", "nope"))
                .isInstanceOf(InvalidShareException.class);
        assertThat(shared).isEmpty();

        service.share(TestUser.JANE, "doc_plain", "client@acme.example");

        assertThat(shared).hasSize(1);
    }

    @Test
    void whoIsAskingGoesToTheStoreWithTheAddressAsSharingComparesIt() {
        service(true).sharedWith(CLIENT);
        service(true).sharedWith(new TestUser("uid-3", " Other@Acme.example", "Other", false));

        assertThat(queriedBy)
                .containsExactly(
                        new Viewer("uid-2", "client@acme.example", true),
                        new Viewer("uid-3", "other@acme.example", false));
    }

    @Test
    void theOwnersCopiesComeMostRecentlyChangedFirst() {
        owned.add(copy("old", Instant.parse("2026-01-01T00:00:00Z")));
        owned.add(copy("new", Instant.parse("2026-02-01T00:00:00Z")));

        assertThat(service(true).sharedBy(TestUser.JANE).stream().map(SharedDocument::id))
                .containsExactly("new", "old");
    }

    @Test
    void anIdNoCopyCouldHaveIsNotFound() {
        assertThatThrownBy(() -> service(true).revoke(TestUser.JANE, "a/b"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Document not found.");
        for (ThrowingCallable call : List.<ThrowingCallable>of(
                () -> service(true).open(CLIENT, "a/b"), () -> service(true).sign(CLIENT, "a/b", "Client"))) {
            assertThatThrownBy(call)
                    .isInstanceOf(NotRecipientException.class)
                    .hasMessage("You do not have access to this shared document.");
        }
    }

    // Opening

    @Test
    void aRecipientOpeningACopyForTheFirstTimeIsNotedAndGetsItAsItWas() {
        copies.put("uid-1_doc_1", sharedWithClient());

        var opened = service(true).open(CLIENT, "uid-1_doc_1");

        assertThat(opened.views()).isEmpty();
        assertThat(noted).containsExactly(new Viewer("uid-2", "client@acme.example", true));
    }

    @Test
    void theOwnerOpeningItIsNotNotedAndAStrangerIsRefused() {
        copies.put("uid-1_doc_1", sharedWithClient());

        service(true).open(TestUser.JANE, "uid-1_doc_1");

        assertThat(noted).isEmpty();
        assertThatThrownBy(() -> service(true).open(new TestUser("uid-9", "x@y.co", "X", true), "uid-1_doc_1"))
                .isInstanceOf(NotRecipientException.class);
        assertThatThrownBy(() -> service(true).open(CLIENT, "missing")).isInstanceOf(NotRecipientException.class);
    }

    @Test
    void aFailureToNoteAnOpeningNeverFailsTheOpening() {
        copies.put("uid-1_doc_1", sharedWithClient());
        notingFails = true;

        assertThat(service(true).open(CLIENT, "uid-1_doc_1").id()).isEqualTo("uid-1_doc_1");
    }

    // Signing

    @Test
    void aSignatureIsCheckedBeforeTheCopyIsLookedUp() {
        for (String invalid : new String[] {"", "   "}) {
            assertThatThrownBy(() -> service(true).sign(CLIENT, "missing", invalid))
                    .isInstanceOf(InvalidShareException.class);
        }
        assertThatThrownBy(() -> service(true).sign(CLIENT, "missing", null)).isInstanceOf(InvalidShareException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void signingStoresTheTrimmedSignatureAndAnnouncesItToTheOwner() {
        copies.put("uid-1_doc_1", sharedWithClient());

        var signed = service(true).sign(CLIENT, "uid-1_doc_1", "  Client Name ");

        assertThat(signed.signatures().get("uid-2").value()).isEqualTo("Client Name");
        assertThat(published)
                .containsExactly(new DocumentSigned(
                        "uid-1_doc_1",
                        "jane@example.com",
                        "Contract",
                        "Client",
                        "Client@Acme.example",
                        "uid-2",
                        NOW_IN_MILLIS));
    }

    @Test
    void nothingIsAnnouncedWhenTheSignatureIsRefused() {
        assertThatThrownBy(() -> service(true).sign(CLIENT, "missing", "Client"))
                .isInstanceOf(NotRecipientException.class);

        assertThat(published).isEmpty();
    }

    private SharingService service(boolean enabled) {
        Documents documents =
                (uid, id) -> uid.equals("uid-1") ? Optional.ofNullable(documentsById.get(id)) : Optional.empty();
        return new SharingService(
                new FakeStore(),
                documents,
                new FreeProfiles(),
                new DokimentsProperties(new DokimentsProperties.Features(enabled, true)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                published::add,
                invites);
    }

    private static SharedDocument copy(String id, Instant updatedAt) {
        return new SharedDocument(
                id, updatedAt, false, "", "", "uid-1", "", List.of(), List.of(), Map.of(), "", "", updatedAt, Map.of(),
                Map.of(), null);
    }

    /** Jane's contract, shared with the client, whose address is verified. */
    private static SharedDocument sharedWithClient() {
        return new SharedDocument(
                "uid-1_doc_1",
                NOW,
                false,
                "Contract",
                "jane@example.com",
                "uid-1",
                "Jane",
                List.of(new Recipient(
                        "client@acme.example", List.of(), NOW, RecipientStatus.PENDING, null, null, null)),
                List.of(),
                Map.of(),
                "doc_1",
                "classic-contract",
                NOW,
                Map.of(),
                Map.of(),
                CONTRACT);
    }

    private static TemplateSnapshot contractTemplate() {
        return new TemplateSnapshot(
                "Contract",
                "",
                "contract",
                List.of(new TemplateSnapshot.Field("recipientSignature", "Signature", null, "text")),
                new TemplateSnapshot.Style("classic", "Classic", "", "free"));
    }

    private final class FakeStore implements SharedDocumentStore {

        @Override
        public List<ShareLedgerEntry> ledger(String ownerUid) {
            return List.of();
        }

        @Override
        public List<SharedDocument> ownedBy(String ownerUid) {
            return owned;
        }

        @Override
        public List<SharedDocument> sharedWith(Viewer viewer) {
            queriedBy.add(viewer);
            return List.of();
        }

        @Override
        public Optional<SharedDocument> find(String sharedDocumentId) {
            return Optional.ofNullable(copies.get(sharedDocumentId));
        }

        @Override
        public Share share(ShareRequest request) {
            shared.add(request);
            return Share.of(
                    null,
                    request.source(),
                    request.ownerUid(),
                    request.ownerEmail(),
                    request.ownerName(),
                    request.email(),
                    request.now());
        }

        @Override
        public SharedDocument revoke(String ownerUid, String sharedDocumentId, Instant now) {
            throw new NotFoundException("Document not found.");
        }

        @Override
        public SharedDocument sign(String sharedDocumentId, Viewer signer, String value, Instant now) {
            var stored = copies.get(sharedDocumentId);
            if (stored == null) {
                throw new NotRecipientException();
            }
            return stored.signedBy(signer, value, now);
        }

        @Override
        public void recordEmail(String sharedDocumentId, String email, Instant sentAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean acceptInvite(Invite invite, Viewer viewer, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordView(String sharedDocumentId, Viewer viewer, Instant now) {
            if (notingFails) {
                throw new UpstreamUnavailableException("Firestore is unreachable.", null);
            }
            noted.add(viewer);
        }
    }

    /** Every account on the free plan. */
    private static final class FreeProfiles implements UserProfiles {

        @Override
        public UserRole roleOf(String uid) {
            return UserRole.FREE;
        }

        @Override
        public UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile updateProfile(UserProfile current, ProfileChanges changes) {
            throw new UnsupportedOperationException();
        }
    }
}

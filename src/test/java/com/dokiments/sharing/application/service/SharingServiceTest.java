package com.dokiments.sharing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.documents.Documents;
import com.dokiments.documents.OwnedDocument;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.InvalidShareException;
import com.dokiments.sharing.domain.NotSignableException;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
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
    private static final TemplateSnapshot CONTRACT = new TemplateSnapshot(
            "Contract",
            "",
            "contract",
            List.of(new TemplateSnapshot.Field("recipientSignature", "Signature", null, "text")),
            new TemplateSnapshot.Style("classic", "Classic", "", "free"));

    private final List<SharedDocumentStore.ShareRequest> shared = new ArrayList<>();
    private final List<SharedDocument> owned = new ArrayList<>();
    private final List<String> queriedEmails = new ArrayList<>();
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
        assertThat(request.now()).isEqualTo(Instant.parse("2026-11-16T09:00:00.123Z"));
        assertThat(request.limit()).isNull();
    }

    @Test
    void onlyTheOwnersSignableDocumentsCanBeShared() {
        var service = service(true);

        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_missing", "client@acme.example"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Document not found.");
        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_plain", "client@acme.example"))
                .isInstanceOf(NotSignableException.class);
        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_1", null)).isInstanceOf(InvalidShareException.class);
        assertThatThrownBy(() -> service.share(TestUser.JANE, "doc_1", "nope"))
                .isInstanceOf(InvalidShareException.class);
        assertThat(shared).isEmpty();
    }

    @Test
    void anUnverifiedAddressSeesNothingSharedWithIt() {
        var service = service(true);

        assertThat(service.sharedWith(new TestUser("uid-2", "Client@Acme.example", "Client", false)))
                .isEmpty();
        assertThat(queriedEmails).isEmpty();

        service.sharedWith(new TestUser("uid-2", " Client@Acme.example", "Client", true));
        assertThat(queriedEmails).containsExactly("client@acme.example");
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
    }

    private SharingService service(boolean enabled) {
        Documents documents =
                (uid, id) -> uid.equals("uid-1") ? Optional.ofNullable(documentsById.get(id)) : Optional.empty();
        return new SharingService(
                new FakeStore(),
                documents,
                new FreeProfiles(),
                new DokimentsProperties(new DokimentsProperties.Features(enabled, true)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SharedDocument copy(String id, Instant updatedAt) {
        return new SharedDocument(
                id, updatedAt, false, "", "", "uid-1", "", List.of(), Map.of(), "", "", updatedAt, Map.of(), Map.of(),
                null);
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
        public List<SharedDocument> sharedWith(String email) {
            queriedEmails.add(email);
            return List.of();
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

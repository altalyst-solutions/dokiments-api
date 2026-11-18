package com.dokiments.sharing.application.service;

import com.dokiments.documents.Documents;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.DocumentIds;
import com.dokiments.shared.SignedInUser;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.application.port.in.ManageShares;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.application.port.out.SharedDocumentStore.ShareRequest;
import com.dokiments.sharing.domain.InvalidShareException;
import com.dokiments.sharing.domain.NotSignableException;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareEmails;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharePolicy;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
class SharingService implements ManageShares {

    /** The web app's wording for a document of the owner's that doesn't exist. */
    static final String DOCUMENT_NOT_FOUND = "Document not found.";

    private static final Comparator<SharedDocument> RECENTLY_CHANGED_FIRST =
            Comparator.comparing(SharedDocument::updatedAt).reversed();

    private final SharedDocumentStore store;
    private final Documents documents;
    private final UserProfiles profiles;
    private final boolean enabled;
    private final Clock clock;

    SharingService(
            SharedDocumentStore store,
            Documents documents,
            UserProfiles profiles,
            DokimentsProperties properties,
            Clock clock) {
        this.store = store;
        this.documents = documents;
        this.profiles = profiles;
        this.enabled = properties.features().documentSharing();
        this.clock = clock;
    }

    @Override
    public Share share(SignedInUser owner, String documentId, @Nullable String email) {
        requireEnabled();
        if (email == null) {
            throw new InvalidShareException();
        }
        String recipient = ShareEmails.typed(email);
        var source =
                documents.find(owner.uid(), documentId).orElseThrow(() -> new NotFoundException(DOCUMENT_NOT_FOUND));
        if (!SharePolicy.isSignable(source.templateSnapshot())) {
            throw new NotSignableException();
        }
        var limit = SharePolicy.limitFor(profiles.roleOf(owner.uid()));
        return store.share(
                new ShareRequest(source, owner.uid(), owner.email(), owner.displayName(), recipient, limit, now()));
    }

    @Override
    public List<ShareLedgerEntry> ledger(SignedInUser owner) {
        requireEnabled();
        return store.ledger(owner.uid()).stream()
                .sorted(Comparator.comparing(ShareLedgerEntry::lastSharedAt).reversed())
                .toList();
    }

    @Override
    public List<SharedDocument> sharedBy(SignedInUser owner) {
        requireEnabled();
        return store.ownedBy(owner.uid()).stream()
                .sorted(RECENTLY_CHANGED_FIRST)
                .toList();
    }

    @Override
    public List<SharedDocument> sharedWith(SignedInUser user) {
        requireEnabled();
        if (!user.emailVerified()) {
            return List.of();
        }
        return store.sharedWith(ShareEmails.normalize(user.email())).stream()
                .sorted(RECENTLY_CHANGED_FIRST)
                .toList();
    }

    @Override
    public SharedDocument revoke(SignedInUser owner, String sharedDocumentId) {
        requireEnabled();
        if (!DocumentIds.isStorable(sharedDocumentId)) {
            throw new NotFoundException(DOCUMENT_NOT_FOUND);
        }
        return store.revoke(owner.uid(), sharedDocumentId, now());
    }

    /** While sharing is off, its routes don't exist, as in the web app. */
    private void requireEnabled() {
        if (!enabled) {
            throw new NotFoundException();
        }
    }

    /** Milliseconds, the precision the web app stores and answers with. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

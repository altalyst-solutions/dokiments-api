package com.dokiments.sharing.application.service;

import com.dokiments.documents.Documents;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.DocumentIds;
import com.dokiments.shared.SignedInUser;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.application.port.in.ManageShares;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.application.port.out.SharedDocumentStore.ShareRequest;
import com.dokiments.sharing.domain.DocumentSigned;
import com.dokiments.sharing.domain.InvalidShareException;
import com.dokiments.sharing.domain.NotRecipientException;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareEmails;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharePolicy;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Signature;
import com.dokiments.sharing.domain.Viewer;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SharingService implements ManageShares {

    private static final Logger log = LoggerFactory.getLogger(SharingService.class);

    /** The web app's wording for a document of the owner's that doesn't exist. */
    static final String DOCUMENT_NOT_FOUND = "Document not found.";

    private static final Comparator<SharedDocument> RECENTLY_CHANGED_FIRST =
            Comparator.comparing(SharedDocument::updatedAt).reversed();

    private final SharedDocumentStore store;
    private final Documents documents;
    private final UserProfiles profiles;
    private final boolean enabled;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    SharingService(
            SharedDocumentStore store,
            Documents documents,
            UserProfiles profiles,
            DokimentsProperties properties,
            Clock clock,
            ApplicationEventPublisher events) {
        this.store = store;
        this.documents = documents;
        this.profiles = profiles;
        this.enabled = properties.features().documentSharing();
        this.clock = clock;
        this.events = events;
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
        return store.sharedWith(Viewer.of(user)).stream()
                .sorted(RECENTLY_CHANGED_FIRST)
                .toList();
    }

    @Override
    public SharedDocument open(SignedInUser user, String sharedDocumentId) {
        requireEnabled();
        var viewer = Viewer.of(user);
        var copy = find(sharedDocumentId);
        if (!copy.canBeOpenedBy(viewer)) {
            throw new NotRecipientException();
        }
        var now = now();
        if (copy.firstViewBy(viewer, now).isPresent()) {
            noteView(sharedDocumentId, viewer, now);
        }
        return copy;
    }

    @Override
    public SharedDocument sign(SignedInUser user, String sharedDocumentId, @Nullable String signature) {
        requireEnabled();
        // The body is checked before the copy is looked up, as in the web app.
        String value = Signature.typed(signature);
        if (!DocumentIds.isStorable(sharedDocumentId)) {
            throw new NotRecipientException();
        }
        var now = now();
        var signed = store.sign(sharedDocumentId, Viewer.of(user), value, now);
        events.publishEvent(new DocumentSigned(
                signed.id(), signed.ownerEmail(), signed.name(), user.displayName(), user.email(), user.uid(), now));
        return signed;
    }

    @Override
    public SharedDocument revoke(SignedInUser owner, String sharedDocumentId) {
        requireEnabled();
        if (!DocumentIds.isStorable(sharedDocumentId)) {
            throw new NotFoundException(DOCUMENT_NOT_FOUND);
        }
        return store.revoke(owner.uid(), sharedDocumentId, now());
    }

    private SharedDocument find(String sharedDocumentId) {
        if (!DocumentIds.isStorable(sharedDocumentId)) {
            throw new NotRecipientException();
        }
        return store.find(sharedDocumentId).orElseThrow(NotRecipientException::new);
    }

    /**
     * Notes the viewer's first opening. It is a courtesy to the owner, so a failure is logged and never reaches the
     * person who opened the copy.
     */
    private void noteView(String sharedDocumentId, Viewer viewer, Instant now) {
        try {
            store.recordView(sharedDocumentId, viewer, now);
        } catch (RuntimeException e) {
            log.warn("Could not note the first opening of a shared document", e);
        }
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

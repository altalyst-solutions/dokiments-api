package com.dokiments.sharing.application.service;

import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Viewer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Shared copies in memory, for the use case tests of invitations and notices, applying the domain's rules as the real
 * store does. Sharing, signing and taking back aren't needed there.
 */
@NullMarked
final class InMemoryCopies implements SharedDocumentStore {

    final Map<String, SharedDocument> stored = new LinkedHashMap<>();
    final List<String> emailsNoted = new ArrayList<>();

    void put(SharedDocument copy) {
        stored.put(copy.id(), copy);
    }

    @Override
    public List<ShareLedgerEntry> ledger(String ownerUid) {
        return List.of();
    }

    @Override
    public List<SharedDocument> ownedBy(String ownerUid) {
        return stored.values().stream()
                .filter(copy -> copy.ownerId().equals(ownerUid))
                .toList();
    }

    @Override
    public List<SharedDocument> sharedWith(Viewer viewer) {
        return List.of();
    }

    @Override
    public Optional<SharedDocument> find(String sharedDocumentId) {
        return Optional.ofNullable(stored.get(sharedDocumentId));
    }

    @Override
    public void recordEmail(String sharedDocumentId, String email, Instant sentAt) {
        emailsNoted.add(email);
        find(sharedDocumentId)
                .flatMap(copy -> copy.withEmailSent(email, sentAt))
                .ifPresent(this::put);
    }

    @Override
    public boolean acceptInvite(Invite invite, Viewer viewer, Instant now) {
        var copy = stored.get(invite.sharedDocumentId());
        if (copy == null || !copy.acceptsInvite(invite, viewer, now)) {
            return false;
        }
        put(copy.withRecipientUid(viewer));
        return true;
    }

    @Override
    public Share share(ShareRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SharedDocument sign(String sharedDocumentId, Viewer signer, String value, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recordView(String sharedDocumentId, Viewer viewer, Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SharedDocument revoke(String ownerUid, String sharedDocumentId, Instant now) {
        throw new UnsupportedOperationException();
    }
}

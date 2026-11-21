package com.dokiments.sharing.application.port.out;

import com.dokiments.documents.OwnedDocument;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Viewer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Where shared copies and each owner's share ledger are kept. */
public interface SharedDocumentStore {

    /** The owner's share ledger, in no particular order. */
    List<ShareLedgerEntry> ledger(String ownerUid);

    /** The copies an owner has shared, in no particular order, with each recipient's status worked out. */
    List<SharedDocument> ownedBy(String ownerUid);

    /**
     * The copies shared with a user, in no particular order, with each recipient's status worked out: those listing
     * their account, and, only when their address is verified, those listing their address. A copy listing both is
     * returned once.
     */
    List<SharedDocument> sharedWith(Viewer viewer);

    /** One copy, with each recipient's status worked out, whoever it belongs to. */
    Optional<SharedDocument> find(String sharedDocumentId);

    /**
     * Shares {@code request.source()} with {@code request.email()}, as one step: reads the copy and the ledger, applies
     * {@link Share#of} and the share policy, and writes the copy and the ledger entry together. Firestore runs it again
     * if another request changed them in between.
     *
     * @throws com.dokiments.sharing.domain.ShareRaceException if Firestore gave up retrying
     */
    Share share(ShareRequest request);

    /**
     * Takes the owner's copy back from its recipient, as one step with reading it.
     *
     * @throws com.dokiments.shared.error.NotFoundException if the owner has no such copy
     */
    SharedDocument revoke(String ownerUid, String sharedDocumentId, Instant now);

    /**
     * Signs the copy as {@code signer} and locks it, as one step with reading it, so two signatures can't both be
     * written.
     *
     * @param value what they typed, already checked
     * @throws com.dokiments.sharing.domain.NotRecipientException if there is no such copy, or the signer isn't a
     *     recipient with access
     * @throws com.dokiments.sharing.domain.DocumentLockedException if it is signed already
     * @throws com.dokiments.sharing.domain.ViewOnlyException if it has nothing to sign
     */
    SharedDocument sign(String sharedDocumentId, Viewer signer, String value, Instant now);

    /**
     * Notes the viewer's first opening of the copy, as one step with reading it, so an opening is never added to a copy
     * that has just been signed. Does nothing when there is nothing to note.
     */
    void recordView(String sharedDocumentId, Viewer viewer, Instant now);

    /**
     * What to share.
     *
     * @param source the owner's document
     * @param ownerUid the owner's ID
     * @param ownerEmail the owner's address
     * @param ownerName the owner's name
     * @param email whom to share with, lower-case
     * @param limit how many different people the owner's plan may share with, or {@code null} for no limit
     * @param now when it is shared
     */
    record ShareRequest(
            OwnedDocument source,
            String ownerUid,
            String ownerEmail,
            String ownerName,
            String email,
            @Nullable Integer limit,
            Instant now) {}
}

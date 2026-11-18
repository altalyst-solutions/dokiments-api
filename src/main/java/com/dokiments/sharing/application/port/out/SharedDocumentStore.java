package com.dokiments.sharing.application.port.out;

import com.dokiments.documents.OwnedDocument;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Where shared copies and each owner's share ledger are kept. */
public interface SharedDocumentStore {

    /** The owner's share ledger, in no particular order. */
    List<ShareLedgerEntry> ledger(String ownerUid);

    /** The copies an owner has shared, in no particular order, with each recipient's status worked out. */
    List<SharedDocument> ownedBy(String ownerUid);

    /** The copies shared with an address, in no particular order, with each recipient's status worked out. */
    List<SharedDocument> sharedWith(String email);

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

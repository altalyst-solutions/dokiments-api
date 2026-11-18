package com.dokiments.sharing.application.port.in;

import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sharing documents for signature, as their owner, and seeing what was shared with you. Every method answers {@code 404
 * Not found.} while sharing is switched off ({@code DOCUMENT_SHARING_ENABLED}).
 */
public interface ManageShares {

    /**
     * Shares a copy of one of the owner's documents with {@code email}, or refreshes it for the person who has it.
     *
     * @param email the address as typed; the use case checks and cleans it
     * @throws com.dokiments.shared.error.NotFoundException if the owner has no such document, or sharing is off
     * @throws com.dokiments.sharing.domain.NotSignableException if its template has no recipient signature
     */
    Share share(SignedInUser owner, String documentId, @Nullable String email);

    /** Everyone the owner has ever shared with, the most recent first. */
    List<ShareLedgerEntry> ledger(SignedInUser owner);

    /** The copies the owner has shared, the most recently changed first. */
    List<SharedDocument> sharedBy(SignedInUser owner);

    /**
     * The copies shared with the user, the most recently changed first. Only for a verified address: anyone can create
     * an account under an address that isn't theirs, so an unverified one sees nothing.
     */
    List<SharedDocument> sharedWith(SignedInUser user);

    /**
     * Takes a copy back from its recipient, as its owner.
     *
     * @throws com.dokiments.shared.error.NotFoundException if the owner has no such copy, or sharing is off
     */
    SharedDocument revoke(SignedInUser owner, String sharedDocumentId);
}

package com.dokiments.sharing.application.port.in;

import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.domain.Share;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sharing documents for signature, as their owner, and opening and signing what was shared with you. Every method
 * answers {@code 404 Not found.} while sharing is switched off ({@code DOCUMENT_SHARING_ENABLED}).
 */
public interface ManageShares {

    /**
     * Shares a copy of one of the owner's documents with {@code email}, or refreshes it for the person who has it. A
     * document whose template has no recipient signature is shared to view only.
     *
     * @param email the address as typed; the use case checks and cleans it
     * @throws com.dokiments.shared.error.NotFoundException if the owner has no such document, or sharing is off
     */
    Share share(SignedInUser owner, String documentId, @Nullable String email);

    /** Everyone the owner has ever shared with, the most recent first. */
    List<ShareLedgerEntry> ledger(SignedInUser owner);

    /** The copies the owner has shared, the most recently changed first. */
    List<SharedDocument> sharedBy(SignedInUser owner);

    /**
     * The copies shared with the user, the most recently changed first. An address only counts when it is verified:
     * anyone can create an account under an address that isn't theirs. An account an invitation link linked sees its
     * copies either way.
     */
    List<SharedDocument> sharedWith(SignedInUser user);

    /**
     * One copy, for its owner or a recipient. A recipient opening it for the first time is noted, so the owner can see
     * it was opened; that never changes the answer, and never fails it.
     *
     * @throws com.dokiments.sharing.domain.NotRecipientException if there is no such copy, or it isn't the user's to
     *     open
     */
    SharedDocument open(SignedInUser user, String sharedDocumentId);

    /**
     * Signs a copy shared with the user, which locks it. The owner is told afterwards, without the answer waiting.
     *
     * @param signature what they typed, as sent; the use case checks and cleans it
     * @throws com.dokiments.sharing.domain.InvalidShareException if the signature is missing, empty or too long
     * @throws com.dokiments.sharing.domain.NotRecipientException if the copy isn't shared with the user
     * @throws com.dokiments.sharing.domain.DocumentLockedException if it is signed already
     * @throws com.dokiments.sharing.domain.ViewOnlyException if it was shared to view, with nothing to sign
     */
    SharedDocument sign(SignedInUser user, String sharedDocumentId, @Nullable String signature);

    /**
     * Takes a copy back from its recipient, as its owner.
     *
     * @throws com.dokiments.shared.error.NotFoundException if the owner has no such copy, or sharing is off
     */
    SharedDocument revoke(SignedInUser owner, String sharedDocumentId);
}

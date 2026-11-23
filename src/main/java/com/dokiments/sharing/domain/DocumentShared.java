package com.dokiments.sharing.domain;

import java.time.Instant;

/**
 * A copy was shared with someone, or refreshed for them. Announced once it is stored, so they can be emailed without
 * the request that shared waiting for it.
 *
 * @param sharedDocumentId the copy's ID
 * @param ownerUid the owner's ID
 * @param ownerEmail the owner's address, which names them when they have no name
 * @param ownerName the owner's name as the account has it, possibly empty
 * @param recipientEmail whom it was shared with, lower-case
 * @param documentName the document's name
 * @param created whether it is newly shared with them, rather than refreshed
 * @param signable whether they are asked to sign it, rather than only view it
 * @param sharedAt when it was shared
 */
public record DocumentShared(
        String sharedDocumentId,
        String ownerUid,
        String ownerEmail,
        String ownerName,
        String recipientEmail,
        String documentName,
        boolean created,
        boolean signable,
        Instant sharedAt) {}

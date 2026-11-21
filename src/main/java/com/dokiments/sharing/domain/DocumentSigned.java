package com.dokiments.sharing.domain;

import java.time.Instant;

/**
 * A recipient has signed a shared copy. Announced once the signature is stored, so the owner can be told without the
 * request that signed waiting for it.
 *
 * @param sharedDocumentId the copy's ID
 * @param ownerEmail whom to tell
 * @param documentName the document's name
 * @param signerName the signer's name as the account has it, possibly empty
 * @param signerEmail the signer's address, which names them when they have no name
 * @param signerUid the signer's account ID
 * @param signedAt when they signed
 */
public record DocumentSigned(
        String sharedDocumentId,
        String ownerEmail,
        String documentName,
        String signerName,
        String signerEmail,
        String signerUid,
        Instant signedAt) {}

package com.dokiments.documents;

import java.util.Optional;

/** A user's own documents, for the modules that work with them. */
public interface Documents {

    /**
     * One of the user's documents. Only the user {@code uid}'s own are ever found: another user's document is empty,
     * like one that doesn't exist.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the document couldn't be read
     */
    Optional<OwnedDocument> find(String uid, String documentId);
}

package com.dokiments.documents.application.port.out;

import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.UserDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Where each user's documents are kept. Every method works only on the documents of the user {@code uid}. */
public interface DocumentStore {

    /** All of the user's documents, in the order they are stored, by ID. */
    List<UserDocument> all(String uid);

    Optional<UserDocument> find(String uid, DocumentId id);

    /** Stores a new document. */
    void add(String uid, UserDocument document);

    /**
     * Stores only what {@code changes} gives, and the time of the change, leaving the rest as it is.
     *
     * @return {@code false} if the document no longer exists, in which case nothing is stored
     */
    boolean change(String uid, DocumentId id, DocumentChanges changes, Instant changedAt);

    /** Removes the document, if it exists. */
    void remove(String uid, DocumentId id);
}

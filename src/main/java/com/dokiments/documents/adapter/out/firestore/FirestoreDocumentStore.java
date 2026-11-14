package com.dokiments.documents.adapter.out.firestore;

import com.dokiments.documents.application.port.out.DocumentStore;
import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.UserDocument;
import com.dokiments.platform.firestore.FirestoreCallException;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Each user's documents, in the subcollection {@code users/{uid}/documents}, as the web app keeps them. The path always
 * starts from the signed-in user's ID, so another user's document can't be reached whatever ID is asked for.
 */
@Repository
class FirestoreDocumentStore implements DocumentStore {

    private final Firestore firestore;

    FirestoreDocumentStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<UserDocument> all(String uid) {
        return FirestoreCalls.await(documents(uid).get()).getDocuments().stream()
                .map(DocumentFields::toDocument)
                .toList();
    }

    @Override
    public Optional<UserDocument> find(String uid, DocumentId id) {
        var document = FirestoreCalls.await(document(uid, id).get());
        return document.exists() ? Optional.of(DocumentFields.toDocument(document)) : Optional.empty();
    }

    @Override
    public void add(String uid, UserDocument document) {
        // create() rather than set(), so an ID that somehow exists already fails instead of replacing that document.
        FirestoreCalls.await(document(uid, document.id()).create(DocumentFields.of(document)));
    }

    @Override
    public boolean change(String uid, DocumentId id, DocumentChanges changes, Instant changedAt) {
        try {
            // update() writes only these fields, and fails when the document doesn't exist instead of creating it.
            FirestoreCalls.await(document(uid, id).update(DocumentFields.of(changes, changedAt)));
            return true;
        } catch (FirestoreCallException e) {
            if (e.code() == StatusCode.Code.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void remove(String uid, DocumentId id) {
        // Deleting a document that doesn't exist succeeds.
        FirestoreCalls.await(document(uid, id).delete());
    }

    private CollectionReference documents(String uid) {
        return firestore.collection("users").document(uid).collection("documents");
    }

    private DocumentReference document(String uid, DocumentId id) {
        return documents(uid).document(id.value());
    }
}

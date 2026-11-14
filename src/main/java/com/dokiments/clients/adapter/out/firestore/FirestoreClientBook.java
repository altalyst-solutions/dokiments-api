package com.dokiments.clients.adapter.out.firestore;

import com.dokiments.clients.application.port.out.ClientBook;
import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientId;
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
 * Each user's clients, in the subcollection {@code users/{uid}/clients}, as the web app keeps them. The path always
 * starts from the signed-in user's ID, so a client of someone else can't be reached whatever ID is asked for.
 */
@Repository
class FirestoreClientBook implements ClientBook {

    private final Firestore firestore;

    FirestoreClientBook(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<Client> all(String uid) {
        return FirestoreCalls.await(clients(uid).get()).getDocuments().stream()
                .map(ClientDocument::toClient)
                .toList();
    }

    @Override
    public Optional<Client> find(String uid, ClientId id) {
        var document = FirestoreCalls.await(client(uid, id).get());
        return document.exists() ? Optional.of(ClientDocument.toClient(document)) : Optional.empty();
    }

    @Override
    public void add(String uid, Client client) {
        // create() rather than set(), so an ID that somehow exists already fails instead of replacing that client.
        FirestoreCalls.await(client(uid, client.id()).create(ClientDocument.of(client)));
    }

    @Override
    public boolean change(String uid, ClientId id, ClientChanges changes, Instant changedAt) {
        try {
            // update() writes only these fields, and fails when the document doesn't exist instead of creating it.
            FirestoreCalls.await(client(uid, id).update(ClientDocument.of(changes, changedAt)));
            return true;
        } catch (FirestoreCallException e) {
            if (e.code() == StatusCode.Code.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void remove(String uid, ClientId id) {
        // Deleting a document that doesn't exist succeeds.
        FirestoreCalls.await(client(uid, id).delete());
    }

    private CollectionReference clients(String uid) {
        return firestore.collection("users").document(uid).collection("clients");
    }

    private DocumentReference client(String uid, ClientId id) {
        return clients(uid).document(id.value());
    }
}

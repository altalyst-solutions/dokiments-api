package com.dokiments.newsletter.adapter.out.firestore;

import com.dokiments.newsletter.application.port.out.NewsletterSubscribers;
import com.dokiments.newsletter.domain.Subscriber;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.cloud.firestore.Firestore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Repository;

/**
 * Subscribers in the {@code newsletter} collection, in the shape the web app writes: {@code { email, status:
 * "subscribed", createdAt }}.
 *
 * <p>The document ID is the SHA-256 of the address, so the address never appears in a document path (paths end up in
 * logs and Firestore's own tooling) and subscribing again replaces the same document instead of adding another. It is
 * computed exactly as the web app does, so subscriptions made by either one are the same documents.
 */
@Repository
class FirestoreNewsletterSubscribers implements NewsletterSubscribers {

    static final String COLLECTION = "newsletter";

    private final Firestore firestore;

    FirestoreNewsletterSubscribers(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void save(Subscriber subscriber) {
        // A full replacement, not a merge, as the web app writes it: the document holds only these fields.
        FirestoreCalls.await(firestore
                .collection(COLLECTION)
                .document(documentId(subscriber.email().value()))
                .set(NewsletterDocument.from(subscriber).toMap()));
    }

    /** The hexadecimal SHA-256 of the cleaned address. */
    static String documentId(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}

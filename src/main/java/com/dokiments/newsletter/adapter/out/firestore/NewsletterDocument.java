package com.dokiments.newsletter.adapter.out.firestore;

import com.dokiments.newsletter.domain.Subscriber;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.cloud.Timestamp;
import java.util.Map;

/**
 * A subscriber as stored. Field names and values are fixed by the documents that already exist.
 *
 * @param email the cleaned address
 * @param status always {@code subscribed}: there is no unsubscribed state yet
 * @param createdAt when they last subscribed
 */
record NewsletterDocument(String email, String status, Timestamp createdAt) {

    static final String SUBSCRIBED = "subscribed";

    static NewsletterDocument from(Subscriber subscriber) {
        return new NewsletterDocument(
                subscriber.email().value(), SUBSCRIBED, FirestoreCalls.toTimestamp(subscriber.subscribedAt()));
    }

    Map<String, Object> toMap() {
        return Map.of("email", email, "status", status, "createdAt", createdAt);
    }
}

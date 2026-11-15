package com.dokiments.feedback.adapter.out.firestore;

import com.dokiments.feedback.application.port.out.FeedbackInbox;
import com.dokiments.feedback.domain.Feedback;
import com.dokiments.feedback.domain.FeedbackThrottle;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Feedback in the top-level {@code feedback} collection, and each account's time of sending in
 * {@code users/{uid}/feedbackMeta/latest}, as the web app keeps them.
 *
 * <p>Feedback is top-level, unlike every other user's data, because an administrator reads everyone's. The time of
 * sending has a document of its own rather than fields on the profile, so the profile keeps its shape.
 */
@Repository
class FirestoreFeedbackInbox implements FeedbackInbox {

    private static final String COUNT = "count";
    private static final String LAST_FEEDBACK_AT = "lastFeedbackAt";

    private final Firestore firestore;

    FirestoreFeedbackInbox(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public boolean keep(Feedback feedback) {
        var stored = firestore.collection("feedback").document(feedback.id().value());
        var meta = firestore
                .collection("users")
                .document(feedback.uid())
                .collection("feedbackMeta")
                .document("latest");
        // A transaction, so the throttle is checked and both documents are written as one step: Firestore runs it
        // again if another submission changed the time in between, and the feedback is never kept without its time.
        return FirestoreCalls.await(firestore.runTransaction(transaction -> {
            var current = transaction.get(meta).get();
            Instant lastSentAt =
                    current.get(LAST_FEEDBACK_AT) instanceof Timestamp last ? FirestoreCalls.toInstant(last) : null;
            if (!FeedbackThrottle.allows(lastSentAt, feedback.createdAt())) {
                return false;
            }
            long count = current.get(COUNT) instanceof Long previous ? previous : 0;
            transaction.create(stored, fields(feedback));
            // A merge of these two fields only, so fields added to the document later are left alone.
            transaction.set(
                    meta,
                    Map.of(COUNT, count + 1, LAST_FEEDBACK_AT, FirestoreCalls.toTimestamp(feedback.createdAt())),
                    SetOptions.merge());
            return true;
        }));
    }

    private static Map<String, Object> fields(Feedback feedback) {
        var message = feedback.message();
        return Map.of(
                "createdAt", FirestoreCalls.toTimestamp(feedback.createdAt()),
                "displayName", feedback.displayName(),
                "email", feedback.email(),
                "message", message.message(),
                "path", message.path(),
                "type", message.type().value(),
                "uid", feedback.uid());
    }
}

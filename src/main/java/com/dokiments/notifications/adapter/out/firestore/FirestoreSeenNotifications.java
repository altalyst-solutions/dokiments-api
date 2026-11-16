package com.dokiments.notifications.adapter.out.firestore;

import com.dokiments.notifications.application.port.out.SeenNotifications;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * When each user last looked, in {@code users/{uid}/notificationMeta/state} as {@code lastSeenAt}, as the web app keeps
 * it.
 */
@Repository
class FirestoreSeenNotifications implements SeenNotifications {

    private static final String LAST_SEEN_AT = "lastSeenAt";

    private final Firestore firestore;

    FirestoreSeenNotifications(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Instant seenAt(String uid) {
        var state = FirestoreCalls.await(state(uid).get());
        return state.get(LAST_SEEN_AT) instanceof Timestamp seenAt ? FirestoreCalls.toInstant(seenAt) : Instant.EPOCH;
    }

    @Override
    public void markSeen(String uid, Instant seenAt) {
        // A merge of this one field, so fields added to the document later are left alone.
        FirestoreCalls.await(
                state(uid).set(Map.of(LAST_SEEN_AT, FirestoreCalls.toTimestamp(seenAt)), SetOptions.merge()));
    }

    private DocumentReference state(String uid) {
        return firestore
                .collection("users")
                .document(uid)
                .collection("notificationMeta")
                .document("state");
    }
}

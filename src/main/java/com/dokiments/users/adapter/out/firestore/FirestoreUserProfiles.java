package com.dokiments.users.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCallException;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.application.port.out.UserProfileStore;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Profiles in the {@code users} collection, one document per account, under its Firebase Auth ID. */
@Repository
class FirestoreUserProfiles implements UserProfileStore {

    static final String COLLECTION = "users";

    private final Firestore firestore;

    FirestoreUserProfiles(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Optional<UserProfile> find(String uid) {
        var snapshot = FirestoreCalls.await(document(uid).get());
        return snapshot.exists() ? Optional.of(UserDocument.read(uid, snapshot)) : Optional.empty();
    }

    @Override
    public void createIfAbsent(UserProfile profile, Instant createdAt) {
        try {
            FirestoreCalls.await(document(profile.uid()).create(UserDocument.forNewProfile(profile, createdAt)));
        } catch (FirestoreCallException e) {
            // Another sign-in of the same new account created it first: it stays as that one wrote it.
            if (e.code() != StatusCode.Code.ALREADY_EXISTS) {
                throw e;
            }
        }
    }

    @Override
    public void markGoogleLinked(String uid) {
        FirestoreCalls.await(document(uid).set(Map.of("linkedGoogle", true), SetOptions.merge()));
    }

    @Override
    public void update(String uid, ProfileChanges changes, Instant updatedAt) {
        // A merge of exactly these fields, as the web app writes them: the sign-in address, role, provider and
        // creation time stay as they are.
        FirestoreCalls.await(document(uid).set(UserDocument.forChanges(changes, updatedAt), SetOptions.merge()));
    }

    private DocumentReference document(String uid) {
        return firestore.collection(COLLECTION).document(uid);
    }
}

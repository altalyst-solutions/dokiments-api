package com.dokiments.users.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCallException;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.users.UserProfile;
import com.dokiments.users.application.port.out.UserProfileStore;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
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

    private DocumentReference document(String uid) {
        return firestore.collection(COLLECTION).document(uid);
    }
}

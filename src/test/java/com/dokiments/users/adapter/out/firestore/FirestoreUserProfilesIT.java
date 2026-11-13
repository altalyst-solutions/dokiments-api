package com.dokiments.users.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Profiles as stored, including documents the web app wrote over the years. */
@IntegrationTest
class FirestoreUserProfilesIT {

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirestoreUserProfiles profiles;

    @Test
    void aNewProfileIsStoredInTheWebAppsShape() {
        String uid = UUID.randomUUID().toString();

        profiles.createIfAbsent(newProfile(uid), Instant.parse("2026-11-12T09:00:00Z"));

        var stored =
                FirestoreCalls.await(firestore.collection("users").document(uid).get());
        assertThat(stored.getData())
                .containsOnlyKeys("createdAt", "updatedAt", "displayName", "email", "provider", "role")
                .containsEntry("role", "free")
                .containsEntry("provider", "password");
    }

    @Test
    void aProfileThatAlreadyExistsIsLeftAsItIs() {
        String uid = UUID.randomUUID().toString();
        profiles.createIfAbsent(newProfile(uid), Instant.now());
        FirestoreCalls.await(firestore.collection("users").document(uid).update("role", "gold"));

        // As when two first sign-ins of the same account race: the second must neither fail nor overwrite.
        profiles.createIfAbsent(newProfile(uid), Instant.now());

        assertThat(profiles.find(uid)).get().extracting(UserProfile::role).isEqualTo(UserRole.GOLD);
    }

    @Test
    void oldOrIncompleteDocumentsAreReadForgivingly() {
        String uid = UUID.randomUUID().toString();
        FirestoreCalls.await(firestore
                .collection("users")
                .document(uid)
                .set(Map.of("email", "jane.doe@example.com", "role", "emperor", "companyName", "", "phone", "123")));

        UserProfile profile = profiles.find(uid).orElseThrow();

        assertThat(profile.displayName()).isEqualTo("jane.doe");
        assertThat(profile.role()).isEqualTo(UserRole.FREE);
        assertThat(profile.provider()).isEqualTo(AuthProvider.PASSWORD);
        assertThat(profile.linkedGoogle()).isFalse();
        assertThat(profile.details().companyName()).isNull();
        assertThat(profile.details().phone()).isEqualTo("123");
    }

    @Test
    void aMissingProfileIsEmpty() {
        assertThat(profiles.find(UUID.randomUUID().toString())).isEmpty();
    }

    /** A profile as a first sign-in creates it. */
    private static UserProfile newProfile(String uid) {
        return new UserProfile(
                uid, "jane@example.com", "Jane", UserRole.FREE, AuthProvider.PASSWORD, false, UserProfile.Details.NONE);
    }
}

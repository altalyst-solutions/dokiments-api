package com.dokiments.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import com.dokiments.users.application.port.out.UserProfileStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

// The port it fakes is null-marked, like the production code.
@NullMarked
class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final Map<String, UserProfile> stored = new HashMap<>();
    private final UserProfileStore store = new UserProfileStore() {
        @Override
        public Optional<UserProfile> find(String uid) {
            return Optional.ofNullable(stored.get(uid));
        }

        @Override
        public void createIfAbsent(UserProfile profile, Instant createdAt) {
            stored.putIfAbsent(profile.uid(), profile);
        }

        @Override
        public void markGoogleLinked(String uid) {
            linked.add(uid);
        }

        @Override
        public void update(String uid, ProfileChanges changes, Instant updatedAt) {
            updates.add(uid + " " + changes.displayName() + " " + updatedAt);
        }
    };
    private final List<String> updates = new ArrayList<>();
    private final List<String> linked = new ArrayList<>();
    private final UserProfileService service = new UserProfileService(store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aFirstSignInCreatesAFreeProfile() {
        UserProfile profile = service.findOrCreate("uid-1", "jane@example.com", "Jane", AuthProvider.PASSWORD);

        assertThat(profile.role()).isEqualTo(UserRole.FREE);
        assertThat(profile.displayName()).isEqualTo("Jane");
        assertThat(profile.provider()).isEqualTo(AuthProvider.PASSWORD);
        assertThat(stored).containsKey("uid-1");
    }

    @Test
    void theRoleIsAsStoredAndFreeWithoutAProfile() {
        var jane = service.findOrCreate("uid-1", "jane@example.com", "Jane", AuthProvider.PASSWORD);
        stored.put(
                "uid-1",
                new UserProfile(
                        jane.uid(),
                        jane.email(),
                        jane.displayName(),
                        UserRole.GOLD,
                        jane.provider(),
                        jane.linkedGoogle(),
                        jane.details()));

        assertThat(service.roleOf("uid-1")).isEqualTo(UserRole.GOLD);
        assertThat(service.roleOf("uid-unknown")).isEqualTo(UserRole.FREE);
    }

    @Test
    void withoutANameTheAddressBeforeTheAtIsUsed() {
        assertThat(service.findOrCreate("uid-1", "jane.doe@example.com", "  ", AuthProvider.GOOGLE)
                        .displayName())
                .isEqualTo("jane.doe");
    }

    @Test
    void aChangeInSettingsIsStoredAndReturnedWithEmptyFieldsAsEmpty() {
        var current = service.findOrCreate("uid-1", "jane@example.com", "Jane", AuthProvider.PASSWORD);

        UserProfile changed =
                service.updateProfile(current, new ProfileChanges("Jane Doe", "", "", "", "Acme", "", "", "", ""));

        assertThat(updates).containsExactly("uid-1 Jane Doe " + NOW);
        assertThat(changed.displayName()).isEqualTo("Jane Doe");
        assertThat(changed.details().companyName()).isEqualTo("Acme");
        assertThat(changed.details().address()).isEmpty();
        assertThat(changed.role()).isEqualTo(current.role());
    }

    @Test
    void theFirstGoogleSignInToAPasswordAccountMarksItLinked() {
        service.findOrCreate("uid-1", "jane@example.com", "Jane", AuthProvider.PASSWORD);

        UserProfile profile = service.findOrCreate("uid-1", "jane@example.com", "Jane", AuthProvider.GOOGLE);

        assertThat(profile.linkedGoogle()).isTrue();
        assertThat(profile.provider()).isEqualTo(AuthProvider.PASSWORD);
        assertThat(linked).containsExactly("uid-1");
    }

    @Test
    void aGoogleAccountIsNeverMarkedLinked() {
        service.findOrCreate("uid-1", "g@example.com", "Gee", AuthProvider.GOOGLE);

        assertThat(service.findOrCreate("uid-1", "g@example.com", "Gee", AuthProvider.GOOGLE)
                        .linkedGoogle())
                .isFalse();
        assertThat(linked).isEmpty();
    }

    @Test
    void anExistingProfileIsReturnedAsStored() {
        var gold = new UserProfile(
                "uid-1",
                "jane@example.com",
                "Jane",
                UserRole.GOLD,
                AuthProvider.PASSWORD,
                false,
                UserProfile.Details.NONE);
        stored.put("uid-1", gold);

        assertThat(service.findOrCreate("uid-1", "jane@example.com", "Someone else", AuthProvider.PASSWORD))
                .isEqualTo(gold);
    }
}

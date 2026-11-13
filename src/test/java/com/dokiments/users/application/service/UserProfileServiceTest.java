package com.dokiments.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import com.dokiments.users.application.port.out.UserProfileStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
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
    };
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
    void withoutANameTheAddressBeforeTheAtIsUsed() {
        assertThat(service.findOrCreate("uid-1", "jane.doe@example.com", "  ", AuthProvider.GOOGLE)
                        .displayName())
                .isEqualTo("jane.doe");
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

        assertThat(service.findOrCreate("uid-1", "jane@example.com", "Someone else", AuthProvider.GOOGLE))
                .isEqualTo(gold);
    }
}

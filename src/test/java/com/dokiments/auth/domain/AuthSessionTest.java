package com.dokiments.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.auth.TestUsers;
import com.dokiments.users.UserRole;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** How long a session lasts, and when its tokens are refreshed. */
class AuthSessionTest {

    private static final Instant SIGNED_IN = Instant.parse("2026-11-12T09:00:00Z");

    private final AuthSession session = TestUsers.session(SIGNED_IN, SIGNED_IN.plus(Duration.ofHours(1)));

    @Test
    void aSessionEndsOneDayAfterSignIn() {
        assertThat(session.expiresAt()).isEqualTo(SIGNED_IN.plus(Duration.ofDays(1)));
        assertThat(session.isExpired(SIGNED_IN.plus(Duration.ofHours(23)))).isFalse();
        assertThat(session.isExpired(SIGNED_IN.plus(Duration.ofDays(1)))).isTrue();
    }

    @Test
    void theIdTokenIsRefreshedInItsLastMinute() {
        Instant idTokenEnd = session.tokens().idTokenExpiresAt();

        assertThat(session.needsRefresh(idTokenEnd.minusSeconds(61))).isFalse();
        assertThat(session.needsRefresh(idTokenEnd.minusSeconds(60))).isTrue();
        assertThat(session.needsRefresh(idTokenEnd.plusSeconds(1))).isTrue();
    }

    @Test
    void refreshingNeverMovesTheEndOfTheSession() {
        Instant later = SIGNED_IN.plus(Duration.ofHours(20));

        AuthSession refreshed = session.refreshed(
                TestUsers.jane(UserRole.GOLD), true, TestUsers.tokens("second", later.plus(Duration.ofHours(1))));

        assertThat(refreshed.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(refreshed.user().role()).isEqualTo(UserRole.GOLD);
        assertThat(refreshed.emailVerified()).isTrue();
        assertThat(refreshed.tokens().idToken()).isEqualTo("id-second");
    }

    @Test
    void tokensAreNeverPrinted() {
        assertThat(session.tokens().toString()).doesNotContain("id-first", "refresh-first");
    }
}

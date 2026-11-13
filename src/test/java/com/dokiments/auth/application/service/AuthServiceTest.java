package com.dokiments.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.auth.TestUsers;
import com.dokiments.auth.application.port.in.SignIn.SignInCommand;
import com.dokiments.auth.application.port.in.SignUp.SignUpCommand;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.AuthTokens;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserProfiles;
import com.dokiments.users.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

// The ports it fakes are null-marked, like the production code.
@NullMarked
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final FakeFirebase firebase = new FakeFirebase();
    private final List<String> profilesCreated = new ArrayList<>();
    private UserRole storedRole = UserRole.FREE;
    private final UserProfiles profiles = (uid, email, displayName, provider) -> {
        profilesCreated.add(uid + " " + displayName + " " + provider);
        return new UserProfile(uid, email, "Jane", storedRole, provider, false, UserProfile.Details.NONE);
    };
    private final AuthService service = new AuthService(firebase, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signingUpCreatesTheProfileSendsTheVerificationEmailAndStartsTheDay() {
        AuthSession session = service.signUp(new SignUpCommand("Jane", "jane@example.com", "secret"));

        assertThat(profilesCreated).containsExactly("uid-jane Jane PASSWORD");
        assertThat(firebase.verificationEmails).containsExactly("id-signed-up");
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(session.tokens().idToken()).isEqualTo("id-signed-up");
    }

    @Test
    void aVerificationEmailThatFailsDoesntFailTheSignUp() {
        firebase.verificationFails = true;

        assertThat(service.signUp(new SignUpCommand("Jane", "jane@example.com", "secret")))
                .isNotNull();
    }

    @Test
    void signingInReadsTheProfile() {
        storedRole = UserRole.GOLD;

        AuthSession session = service.signIn(new SignInCommand("jane@example.com", "secret"));

        assertThat(session.user().role()).isEqualTo(UserRole.GOLD);
        assertThat(firebase.verificationEmails).isEmpty();
    }

    @Test
    void aRefreshPicksUpProfileChangesAndKeepsTheEndOfTheSession() {
        AuthSession session = TestUsers.session(NOW.minus(Duration.ofHours(5)), NOW);
        storedRole = UserRole.SILVER;

        AuthSession refreshed = service.refresh(session);

        assertThat(refreshed.tokens().idToken()).isEqualTo("id-refreshed-1");
        assertThat(refreshed.user().role()).isEqualTo(UserRole.SILVER);
        assertThat(refreshed.expiresAt()).isEqualTo(session.expiresAt());
    }

    @Test
    void aRefusedRefreshIsReportedAsIs() {
        firebase.refreshRefused = true;

        assertThatThrownBy(() -> service.refresh(TestUsers.session(NOW, NOW)))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void refreshesOfTheSameSessionAtOnceShareOneCallToFirebase() throws Exception {
        firebase.refreshGate = new CountDownLatch(1);
        AuthSession session = TestUsers.session(NOW, NOW);

        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> service.refresh(session));
            var second = threads.submit(() -> service.refresh(session));
            // Both are waiting on the one call by now; let it answer.
            assertThat(firebase.refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            firebase.refreshGate.countDown();

            assertThat(first.get().tokens()).isEqualTo(second.get().tokens());
        }
        assertThat(firebase.refreshCalls.get()).isEqualTo(1);
    }

    /** Firebase Auth as far as these tests need it. */
    static final class FakeFirebase implements IdentityProvider {

        final List<String> verificationEmails = new ArrayList<>();
        final AtomicInteger refreshCalls = new AtomicInteger();
        final CountDownLatch refreshStarted = new CountDownLatch(1);

        @Nullable CountDownLatch refreshGate;

        boolean verificationFails;
        boolean refreshRefused;

        @Override
        public SignedIn signUp(String displayName, String email, String password) {
            return new SignedIn("uid-jane", email, displayName, false, tokens("signed-up"));
        }

        @Override
        public SignedIn signIn(String email, String password) {
            return new SignedIn("uid-jane", email, "Jane", true, tokens("signed-in"));
        }

        @Override
        public Refreshed refresh(String refreshToken) {
            int call = refreshCalls.incrementAndGet();
            refreshStarted.countDown();
            if (refreshGate != null) {
                try {
                    if (!refreshGate.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("The test never let the refresh answer");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (refreshRefused) {
                throw new UnauthenticatedException("Your session expired. Please sign in again.", null);
            }
            return new Refreshed("uid-jane", true, tokens("refreshed-" + call));
        }

        @Override
        public void sendVerificationEmail(String idToken) {
            if (verificationFails) {
                throw new IllegalStateException("Firebase refused the verification email");
            }
            verificationEmails.add(idToken);
        }

        private static AuthTokens tokens(String name) {
            return new AuthTokens("id-" + name, "refresh-" + name, NOW.plus(Duration.ofHours(1)));
        }
    }
}

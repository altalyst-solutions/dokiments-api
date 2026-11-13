package com.dokiments.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.auth.TestUsers;
import com.dokiments.auth.application.port.in.ManageAccount.ResetPasswordCommand;
import com.dokiments.auth.application.port.in.ManageAccount.UpdateProfileCommand;
import com.dokiments.auth.application.port.in.SignIn.SignInCommand;
import com.dokiments.auth.application.port.in.SignUp.SignUpCommand;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.AuthTokens;
import com.dokiments.auth.domain.ReauthenticationRequiredException;
import com.dokiments.shared.error.InvalidRequestException;
import com.dokiments.shared.error.TooManyRequestsException;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.ProfileChanges;
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
    private final FakeProfiles profiles = new FakeProfiles();
    private final AuthService service = new AuthService(firebase, profiles, Clock.fixed(NOW, ZoneOffset.UTC));

    // Signing up and in

    @Test
    void signingUpCreatesTheProfileSendsTheVerificationEmailAndStartsTheDay() {
        AuthSession session = service.signUp(new SignUpCommand("Jane", "jane@example.com", "secret"));

        assertThat(profiles.created).containsExactly("uid-jane Jane PASSWORD");
        assertThat(firebase.verificationEmails).containsExactly("id-signed-up");
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void aVerificationEmailThatFailsDoesntFailTheSignUp() {
        firebase.refusal = new IdentityProvider.Refused("WHATEVER", "No.");

        assertThat(service.signUp(new SignUpCommand("Jane", "jane@example.com", "secret")))
                .isNotNull();
    }

    @Test
    void aRefusedSignInIsUnauthenticatedWithFirebasesMessage() {
        firebase.signInRefusal = new IdentityProvider.Refused("INVALID_LOGIN_CREDENTIALS", "Wrong.");

        assertThatThrownBy(() -> service.signIn(new SignInCommand("jane@example.com", "x")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("Wrong.");
    }

    @Test
    void signingInReadsTheProfile() {
        profiles.role = UserRole.GOLD;

        assertThat(service.signIn(new SignInCommand("jane@example.com", "secret"))
                        .user()
                        .role())
                .isEqualTo(UserRole.GOLD);
    }

    // Refreshing

    @Test
    void aRefreshPicksUpProfileChangesAndKeepsTheEndOfTheSession() {
        AuthSession session = TestUsers.session(NOW.minus(Duration.ofHours(5)), NOW);
        profiles.role = UserRole.SILVER;

        AuthSession refreshed = service.refresh(session);

        assertThat(refreshed.tokens().idToken()).isEqualTo("id-refreshed-1");
        assertThat(refreshed.user().role()).isEqualTo(UserRole.SILVER);
        assertThat(refreshed.expiresAt()).isEqualTo(session.expiresAt());
    }

    @Test
    void aRefusedRefreshIsUnauthenticated() {
        firebase.refusal = new IdentityProvider.Refused("TOKEN_EXPIRED", "Expired.");

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
            assertThat(firebase.refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            firebase.refreshGate.countDown();

            assertThat(first.get().tokens()).isEqualTo(second.get().tokens());
        }
        assertThat(firebase.refreshCalls.get()).isEqualTo(1);
    }

    // Changing the account

    @Test
    void aNewPasswordBringsNewTokensAndTheSessionKeepsItsEnd() {
        AuthSession session = TestUsers.session(NOW.minus(Duration.ofHours(3)), NOW.plusSeconds(600));

        AuthSession changed = service.changePassword(session, "new-secret");

        assertThat(changed.tokens().idToken()).isEqualTo("id-updated");
        assertThat(changed.expiresAt()).isEqualTo(session.expiresAt());
    }

    @Test
    void aGoogleAccountHasNoPasswordToChangeOrVerify() {
        AuthSession google = googleSession();

        assertThatThrownBy(() -> service.changePassword(google, "x"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("This account signs in with Google — there is no password to change.");
        assertThatThrownBy(() -> service.reauthenticate(google, "x"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("This account signs in with Google — there is no password to verify.");
    }

    @Test
    void aGoogleAccountIsToldSoEvenWithAnInvalidPassword() {
        // The web app checks the account before the body.
        assertThatThrownBy(() -> service.changePassword(googleSession(), ""))
                .hasMessageContaining("signs in with Google");
    }

    @Test
    void aSessionTooOldForAPasswordChangeAsksForTheCurrentPassword() {
        firebase.refusal = new IdentityProvider.Refused(
                "CREDENTIAL_TOO_OLD_LOGIN_AGAIN", "For your security, please sign out and sign in again.");

        assertThatThrownBy(() -> service.changePassword(TestUsers.session(NOW, NOW.plusSeconds(600)), "new-secret"))
                .isInstanceOfSatisfying(
                        ReauthenticationRequiredException.class,
                        e -> assertThat(e.publicCode()).isEqualTo("REAUTH_REQUIRED"));
    }

    @Test
    void otherRefusalsOfAPasswordChangeAreAnInvalidRequest() {
        firebase.refusal = new IdentityProvider.Refused("WEAK_PASSWORD", "Use a stronger password.");

        assertThatThrownBy(() -> service.changePassword(TestUsers.session(NOW, NOW.plusSeconds(600)), "a"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Use a stronger password.");
    }

    @Test
    void reauthenticatingUsesTheSessionsOwnAddress() {
        AuthSession session = TestUsers.session(NOW.minus(Duration.ofHours(3)), NOW.plusSeconds(600));

        AuthSession confirmed = service.reauthenticate(session, "secret");

        assertThat(firebase.signIns).containsExactly("jane@example.com");
        assertThat(confirmed.tokens().idToken()).isEqualTo("id-signed-in");
        assertThat(confirmed.expiresAt()).isEqualTo(session.expiresAt());
    }

    @Test
    void aWrongPasswordWhenReauthenticatingIsAnInvalidRequest() {
        firebase.signInRefusal = new IdentityProvider.Refused("INVALID_LOGIN_CREDENTIALS", "Wrong.");

        assertThatThrownBy(() -> service.reauthenticate(TestUsers.session(NOW, NOW.plusSeconds(600)), "x"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Wrong.");
    }

    @Test
    void aProfileChangeUpdatesFirebaseAndTheProfile() {
        AuthSession session = TestUsers.session(NOW, NOW.plusSeconds(600));

        AuthSession changed =
                service.updateProfile(session, UpdateProfileCommand.of("Jane Doe", "", "", "", "Acme", "", "", "", ""));

        assertThat(firebase.displayNames).containsExactly("Jane Doe");
        assertThat(changed.user().displayName()).isEqualTo("Jane Doe");
        assertThat(changed.user().details().companyName()).isEqualTo("Acme");
        assertThat(changed.user().details().address()).isEmpty();
    }

    @Test
    void aVerifiedAddressGetsNoNewVerificationEmail() {
        AuthSession verified = TestUsers.session(NOW, NOW.plusSeconds(600))
                .withTokens(TestUsers.tokens("v", NOW.plusSeconds(600)), true);

        service.resendVerificationEmail(verified);

        assertThat(firebase.verificationEmails).isEmpty();
    }

    @Test
    void verificationEmailsFirebaseThrottlesAreTooManyRequests() {
        firebase.refusal = new IdentityProvider.Refused("TOO_MANY_ATTEMPTS_TRY_LATER", "Too many attempts.");

        assertThatThrownBy(() -> service.resendVerificationEmail(TestUsers.session(NOW, NOW.plusSeconds(600))))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aRefusedPasswordResetIsAnInvalidRequest() {
        firebase.refusal = new IdentityProvider.Refused("EMAIL_NOT_FOUND", "No account was found for this email.");

        assertThatThrownBy(() -> service.sendPasswordResetEmail(new ResetPasswordCommand("nobody@example.com")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("No account was found for this email.");
    }

    private static AuthSession googleSession() {
        var google = new UserProfile(
                "uid-g", "g@example.com", "G", UserRole.FREE, AuthProvider.GOOGLE, false, UserProfile.Details.NONE);
        return AuthSession.start(google, true, TestUsers.tokens("g", NOW.plusSeconds(600)), NOW);
    }

    /** Profiles as far as these tests need them. */
    static final class FakeProfiles implements UserProfiles {

        final List<String> created = new ArrayList<>();
        UserRole role = UserRole.FREE;

        @Override
        public UserProfile findOrCreate(String uid, String email, @Nullable String displayName, AuthProvider provider) {
            created.add(uid + " " + displayName + " " + provider);
            return new UserProfile(uid, email, "Jane", role, provider, false, UserProfile.Details.NONE);
        }

        @Override
        public UserProfile updateProfile(UserProfile current, ProfileChanges changes) {
            return new UserProfile(
                    current.uid(),
                    current.email(),
                    changes.displayName(),
                    current.role(),
                    current.provider(),
                    current.linkedGoogle(),
                    new UserProfile.Details(
                            changes.address(),
                            changes.brn(),
                            changes.businessEmail(),
                            changes.companyName(),
                            changes.fullName(),
                            changes.phone(),
                            changes.tel(),
                            changes.website()));
        }
    }

    /** Firebase Auth as far as these tests need it. */
    static final class FakeFirebase implements IdentityProvider {

        final List<String> verificationEmails = new ArrayList<>();
        final List<String> signIns = new ArrayList<>();
        final List<String> displayNames = new ArrayList<>();
        final List<String> googleTokens = new ArrayList<>();
        final AtomicInteger refreshCalls = new AtomicInteger();
        final CountDownLatch refreshStarted = new CountDownLatch(1);

        @Nullable CountDownLatch refreshGate;

        /** Refuses every call except signing in and up. */
        @Nullable Refused refusal;

        @Nullable Refused signInRefusal;

        @Override
        public SignedIn signUp(String displayName, String email, String password) {
            return new SignedIn("uid-jane", email, displayName, false, tokens("signed-up"));
        }

        @Override
        public SignedIn signIn(String email, String password) {
            if (signInRefusal != null) {
                throw signInRefusal;
            }
            signIns.add(email);
            return new SignedIn("uid-jane", email, "Jane", true, tokens("signed-in"));
        }

        @Override
        public SignedIn signInWithGoogle(String googleIdToken) {
            googleTokens.add(googleIdToken);
            return new SignedIn("uid-google", "g@example.com", "Gee", true, tokens("google"));
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
            refuseIfAsked();
            return new Refreshed("uid-jane", true, tokens("refreshed-" + call));
        }

        @Override
        public void sendVerificationEmail(String idToken) {
            refuseIfAsked();
            verificationEmails.add(idToken);
        }

        @Override
        public void sendPasswordResetEmail(String email) {
            refuseIfAsked();
        }

        @Override
        public Updated changePassword(String idToken, String newPassword) {
            refuseIfAsked();
            return new Updated(tokens("updated"), false);
        }

        @Override
        public Updated changeDisplayName(String idToken, String displayName) {
            refuseIfAsked();
            displayNames.add(displayName);
            return new Updated(null, null);
        }

        private void refuseIfAsked() {
            if (refusal != null) {
                throw refusal;
            }
        }

        private static AuthTokens tokens(String name) {
            return new AuthTokens("id-" + name, "refresh-" + name, NOW.plus(Duration.ofHours(1)));
        }
    }
}

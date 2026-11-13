package com.dokiments.auth.application.service;

import com.dokiments.auth.application.port.in.ManageAccount;
import com.dokiments.auth.application.port.in.RefreshSession;
import com.dokiments.auth.application.port.in.SignIn;
import com.dokiments.auth.application.port.in.SignUp;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.ReauthenticationRequiredException;
import com.dokiments.shared.error.InvalidRequestException;
import com.dokiments.shared.error.TooManyRequestsException;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AuthService implements SignIn, SignUp, RefreshSession, ManageAccount {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final String GOOGLE_HAS_NO_PASSWORD_TO_CHANGE =
            "This account signs in with Google — there is no password to change.";
    static final String GOOGLE_HAS_NO_PASSWORD_TO_VERIFY =
            "This account signs in with Google — there is no password to verify.";

    private final IdentityProvider identityProvider;
    private final UserProfiles profiles;
    private final Clock clock;

    /**
     * Refreshes in progress, by refresh token. A page often sends several requests at once, and each would otherwise
     * refresh the same session separately; they share one refresh instead. Only within this instance: another instance
     * refreshing the same session at the same moment also succeeds, just with tokens of its own.
     */
    private final Map<String, CompletableFuture<AuthSession>> refreshing = new ConcurrentHashMap<>();

    AuthService(IdentityProvider identityProvider, UserProfiles profiles, Clock clock) {
        this.identityProvider = identityProvider;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Override
    public AuthSession signUp(SignUpCommand command) {
        var account = refusedMeansUnauthenticated(
                () -> identityProvider.signUp(command.displayName(), command.email(), command.password()));
        var profile =
                profiles.findOrCreate(account.uid(), account.email(), command.displayName(), AuthProvider.PASSWORD);
        try {
            identityProvider.sendVerificationEmail(account.tokens().idToken());
        } catch (RuntimeException e) {
            // The account exists by now, so this must not fail the sign-up: the user can ask for another email.
            log.warn("The verification email after sign-up couldn't be sent", e);
        }
        return AuthSession.start(profile, account.emailVerified(), account.tokens(), clock.instant());
    }

    @Override
    public AuthSession signIn(SignInCommand command) {
        var account = refusedMeansUnauthenticated(() -> identityProvider.signIn(command.email(), command.password()));
        var profile =
                profiles.findOrCreate(account.uid(), account.email(), account.displayName(), AuthProvider.PASSWORD);
        return AuthSession.start(profile, account.emailVerified(), account.tokens(), clock.instant());
    }

    @Override
    public AuthSession refresh(AuthSession session) {
        String refreshToken = session.tokens().refreshToken();
        var pending = new CompletableFuture<AuthSession>();
        var running = refreshing.putIfAbsent(refreshToken, pending);
        if (running != null) {
            return join(running);
        }
        try {
            pending.complete(refreshNow(session));
        } catch (RuntimeException e) {
            pending.completeExceptionally(e);
        } finally {
            refreshing.remove(refreshToken);
        }
        return join(pending);
    }

    private AuthSession refreshNow(AuthSession session) {
        var refreshed = refusedMeansUnauthenticated(
                () -> identityProvider.refresh(session.tokens().refreshToken()));
        var profile = profiles.findOrCreate(
                refreshed.uid(), session.user().email(), null, session.user().provider());
        return session.refreshed(profile, refreshed.emailVerified(), refreshed.tokens());
    }

    @Override
    public AuthSession changePassword(AuthSession session, String newPassword) {
        if (session.user().provider() == AuthProvider.GOOGLE) {
            throw new InvalidRequestException(GOOGLE_HAS_NO_PASSWORD_TO_CHANGE);
        }
        var command = new NewPasswordCommand(newPassword);
        try {
            return withChanges(
                    session, identityProvider.changePassword(session.tokens().idToken(), command.password()));
        } catch (IdentityProvider.Refused refused) {
            if (refused.is(IdentityProvider.Refused.CREDENTIAL_TOO_OLD)) {
                throw new ReauthenticationRequiredException(refused.userMessage(), refused);
            }
            throw new InvalidRequestException(refused.userMessage(), refused);
        }
    }

    @Override
    public AuthSession reauthenticate(AuthSession session, String currentPassword) {
        if (session.user().provider() == AuthProvider.GOOGLE) {
            throw new InvalidRequestException(GOOGLE_HAS_NO_PASSWORD_TO_VERIFY);
        }
        var command = new CurrentPasswordCommand(currentPassword);
        var account =
                refusedMeansInvalid(() -> identityProvider.signIn(session.user().email(), command.password()));
        var profile =
                profiles.findOrCreate(account.uid(), account.email(), account.displayName(), AuthProvider.PASSWORD);
        return session.refreshed(profile, account.emailVerified(), account.tokens());
    }

    @Override
    public AuthSession updateProfile(AuthSession session, UpdateProfileCommand command) {
        var changes = command.changes();
        var updated = refusedMeansInvalid(
                () -> identityProvider.changeDisplayName(session.tokens().idToken(), changes.displayName()));
        return withChanges(session, updated).withUser(profiles.updateProfile(session.user(), changes));
    }

    @Override
    public void resendVerificationEmail(AuthSession session) {
        if (session.emailVerified()) {
            return;
        }
        try {
            identityProvider.sendVerificationEmail(session.tokens().idToken());
        } catch (IdentityProvider.Refused refused) {
            if (refused.is(IdentityProvider.Refused.TOO_MANY_ATTEMPTS)) {
                throw new TooManyRequestsException(refused.userMessage(), refused);
            }
            throw new InvalidRequestException(refused.userMessage(), refused);
        }
    }

    @Override
    public void sendPasswordResetEmail(ResetPasswordCommand command) {
        try {
            identityProvider.sendPasswordResetEmail(command.email());
        } catch (IdentityProvider.Refused refused) {
            throw new InvalidRequestException(refused.userMessage(), refused);
        }
    }

    /** The session with the new tokens, if Firebase issued any. */
    private static AuthSession withChanges(AuthSession session, IdentityProvider.Updated updated) {
        if (updated.tokens() == null) {
            return session;
        }
        boolean verified = updated.emailVerified() != null ? updated.emailVerified() : session.emailVerified();
        return session.withTokens(updated.tokens(), verified);
    }

    /** When signing in or up, a refusal means the credentials weren't accepted: {@code 401}. */
    private static <T> T refusedMeansUnauthenticated(Supplier<T> call) {
        try {
            return call.get();
        } catch (IdentityProvider.Refused refused) {
            throw new UnauthenticatedException(refused.userMessage(), refused);
        }
    }

    /** For a change to the account, a refusal means the request can't be done as sent: {@code 400}. */
    private static <T> T refusedMeansInvalid(Supplier<T> call) {
        try {
            return call.get();
        } catch (IdentityProvider.Refused refused) {
            throw new InvalidRequestException(refused.userMessage(), refused);
        }
    }

    /** The result, or the same exception the refresh failed with. */
    private static AuthSession join(CompletableFuture<AuthSession> refresh) {
        try {
            return refresh.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}

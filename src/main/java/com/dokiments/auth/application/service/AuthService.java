package com.dokiments.auth.application.service;

import com.dokiments.auth.application.port.in.RefreshSession;
import com.dokiments.auth.application.port.in.SignIn;
import com.dokiments.auth.application.port.in.SignUp;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AuthService implements SignIn, SignUp, RefreshSession {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
        var account = identityProvider.signUp(command.displayName(), command.email(), command.password());
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
        var account = identityProvider.signIn(command.email(), command.password());
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
        var refreshed = identityProvider.refresh(session.tokens().refreshToken());
        var profile = profiles.findOrCreate(
                refreshed.uid(), session.user().email(), null, session.user().provider());
        return session.refreshed(profile, refreshed.emailVerified(), refreshed.tokens());
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

package com.dokiments.auth.application.service;

import com.dokiments.auth.application.port.in.SignInWithGoogle;
import com.dokiments.auth.application.port.out.GoogleOAuth;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.ReturnPath;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
class GoogleSignInService implements SignInWithGoogle {

    private final GoogleOAuth google;
    private final PendingSignIns pending;
    private final IdentityProvider identityProvider;
    private final UserProfiles profiles;
    private final Clock clock;

    GoogleSignInService(
            GoogleOAuth google,
            PendingSignIns pending,
            IdentityProvider identityProvider,
            UserProfiles profiles,
            Clock clock) {
        this.google = google;
        this.pending = pending;
        this.identityProvider = identityProvider;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Override
    public Started start(@Nullable String next) {
        if (!google.configured()) {
            throw new Failed("Google sign-in isn't set up (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)", null);
        }
        // Random and single-use; UUID.randomUUID draws from a cryptographically strong source.
        String state = UUID.randomUUID().toString();
        pending.save(state, ReturnPath.of(next));
        return new Started(google.authorizationUrl(state), state);
    }

    @Override
    public Finished finish(String state, String code) {
        ReturnPath returnPath =
                pending.take(state).orElseThrow(() -> new Failed("Unknown, expired or used sign-in state", null));
        try {
            String googleIdToken = google.exchangeCode(code);
            var account = identityProvider.signInWithGoogle(googleIdToken);
            var profile =
                    profiles.findOrCreate(account.uid(), account.email(), account.displayName(), AuthProvider.GOOGLE);
            var session = AuthSession.start(profile, account.emailVerified(), account.tokens(), clock.instant());
            return new Finished(session, returnPath);
        } catch (RuntimeException e) {
            // Whatever stopped it, Google, Firebase or an outage, the browser goes back to the sign-in page.
            throw new Failed("Google sign-in didn't complete", e);
        }
    }
}

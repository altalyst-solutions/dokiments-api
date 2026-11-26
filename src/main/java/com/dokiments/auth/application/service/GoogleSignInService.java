package com.dokiments.auth.application.service;

import com.dokiments.auth.application.port.in.SignInWithGoogle;
import com.dokiments.auth.application.port.out.GoogleOAuth;
import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.application.port.out.PendingSignIns.PendingSignIn;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.ReturnPath;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
class GoogleSignInService implements SignInWithGoogle {

    /** The longest promo code kept, the same limit as for codes typed at sign-in. */
    private static final int MAX_PROMO_CODE = 64;

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
    public Started start(@Nullable String next, @Nullable String promoCode) {
        if (!google.configured()) {
            throw new Failed("Google sign-in isn't set up (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)", null);
        }
        // Random and single-use; UUID.randomUUID draws from a cryptographically strong source.
        String state = UUID.randomUUID().toString();
        try {
            pending.save(state, new PendingSignIn(ReturnPath.of(next), promoCode(promoCode)));
        } catch (RuntimeException e) {
            // Without the pending sign-in, the callback couldn't be matched: the browser goes back to the sign-in page.
            throw new Failed("The pending sign-in couldn't be stored", e);
        }
        return new Started(google.authorizationUrl(state), state);
    }

    @Override
    public Finished finish(String state, String code) {
        Optional<PendingSignIn> taken;
        try {
            taken = pending.take(state);
        } catch (RuntimeException e) {
            throw new Failed("The pending sign-in couldn't be read", e);
        }
        PendingSignIn signIn = taken.orElseThrow(() -> new Failed("Unknown, expired or used sign-in state", null));
        try {
            String googleIdToken = google.exchangeCode(code);
            var account = identityProvider.signInWithGoogle(googleIdToken);
            var profile =
                    profiles.findOrCreate(account.uid(), account.email(), account.displayName(), AuthProvider.GOOGLE);
            var session = AuthSession.start(profile, account.emailVerified(), account.tokens(), clock.instant());
            return new Finished(session, signIn.returnPath(), signIn.promoCode());
        } catch (RuntimeException e) {
            // Whatever stopped it, Google, Firebase or an outage, the browser goes back to the sign-in page.
            throw new Failed("Google sign-in didn't complete", e);
        }
    }

    /**
     * The code as kept for the end of the sign-in: at most as long as any code, as in the web app, because it arrives
     * in a link anyone can make; empty becomes none.
     */
    private static @Nullable String promoCode(@Nullable String promoCode) {
        if (promoCode == null || promoCode.isEmpty()) {
            return null;
        }
        return promoCode.length() > MAX_PROMO_CODE ? promoCode.substring(0, MAX_PROMO_CODE) : promoCode;
    }
}

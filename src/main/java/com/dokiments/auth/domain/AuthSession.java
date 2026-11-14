package com.dokiments.auth.domain;

import com.dokiments.shared.SignedInUser;
import com.dokiments.users.UserProfile;
import java.time.Duration;
import java.time.Instant;

/**
 * A signed-in session: who is signed in, their Firebase tokens, and when the session ends.
 *
 * <p>A session lasts one day from sign-in, whatever happens in between: refreshing the tokens never moves that
 * deadline, so everyone signs in again at least once a day. The ID token lasts about an hour, so it is refreshed
 * shortly before it runs out.
 *
 * @param user the profile, as of the last sign-in or refresh
 * @param emailVerified whether Firebase has confirmed the address, as of the last sign-in or refresh
 * @param tokens the Firebase tokens
 * @param expiresAt the end of the session, one day after sign-in
 */
public record AuthSession(UserProfile user, boolean emailVerified, AuthTokens tokens, Instant expiresAt)
        implements SignedInUser {

    /** How long a session lasts from sign-in. Not extended by activity or refreshes. */
    public static final Duration MAX_AGE = Duration.ofDays(1);

    /**
     * How long before the ID token runs out it is refreshed. Refreshing early avoids a request failing because the
     * token expired between the check and Firebase receiving it.
     */
    static final Duration REFRESH_AHEAD = Duration.ofSeconds(60);

    /** A new session, starting the one-day clock. */
    public static AuthSession start(UserProfile user, boolean emailVerified, AuthTokens tokens, Instant now) {
        return new AuthSession(user, emailVerified, tokens, now.plus(MAX_AGE));
    }

    /** The signed-in user's ID, which is all that other modules see of the session. */
    @Override
    public String uid() {
        return user.uid();
    }

    /** Whether the session has reached its end and must no longer be accepted. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Whether the ID token runs out within {@link #REFRESH_AHEAD} and should be refreshed. */
    public boolean needsRefresh(Instant now) {
        return !now.plus(REFRESH_AHEAD).isBefore(tokens.idTokenExpiresAt());
    }

    /**
     * The same session with new tokens, for example after a password change, ending at the same time as before.
     *
     * @param nowVerified whether the address is confirmed as of the new tokens
     */
    public AuthSession withTokens(AuthTokens newTokens, boolean nowVerified) {
        return new AuthSession(user, nowVerified, newTokens, expiresAt);
    }

    /** The same session for the profile as it is now, after a change in settings. */
    public AuthSession withUser(UserProfile currentUser) {
        return new AuthSession(currentUser, emailVerified, tokens, expiresAt);
    }

    /** The same session with new tokens and the profile as it is now, ending at the same time as before. */
    public AuthSession refreshed(UserProfile currentUser, boolean nowVerified, AuthTokens newTokens) {
        return new AuthSession(currentUser, nowVerified, newTokens, expiresAt);
    }
}

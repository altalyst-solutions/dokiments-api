package com.dokiments.auth.domain;

import java.time.Instant;

/**
 * The Firebase tokens of a signed-in account. They never leave the server: the browser only holds the session cookie.
 *
 * @param idToken proves who the account is to Firebase; valid for about an hour
 * @param refreshToken exchanged for a new ID token when that one runs out
 * @param idTokenExpiresAt when the ID token stops being valid
 */
public record AuthTokens(String idToken, String refreshToken, Instant idTokenExpiresAt) {

    @Override
    public String toString() {
        // Never in logs or exception messages.
        return "AuthTokens[expires " + idTokenExpiresAt + "]";
    }
}

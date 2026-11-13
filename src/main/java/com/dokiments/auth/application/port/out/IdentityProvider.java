package com.dokiments.auth.application.port.out;

import com.dokiments.auth.domain.AuthTokens;
import org.jspecify.annotations.Nullable;

/**
 * Who checks passwords and issues tokens: Firebase Auth.
 *
 * <p>Every method throws {@link com.dokiments.shared.error.UnauthenticatedException}, with a message for the user, when
 * Firebase refuses the request, and {@link com.dokiments.shared.error.UpstreamUnavailableException} when it can't be
 * reached.
 */
public interface IdentityProvider {

    SignedIn signUp(String displayName, String email, String password);

    SignedIn signIn(String email, String password);

    /** New tokens for a refresh token. */
    Refreshed refresh(String refreshToken);

    /** Asks Firebase to email the account a link to confirm its address. */
    void sendVerificationEmail(String idToken);

    /**
     * An account that has just signed in or up.
     *
     * @param uid its Firebase Auth ID
     * @param email its address
     * @param displayName the name on the account, if it has one
     * @param emailVerified whether its address is confirmed
     * @param tokens its new tokens
     */
    record SignedIn(String uid, String email, @Nullable String displayName, boolean emailVerified, AuthTokens tokens) {}

    /**
     * The result of a refresh.
     *
     * @param uid the account's Firebase Auth ID
     * @param emailVerified whether its address is confirmed now
     * @param tokens the new tokens
     */
    record Refreshed(String uid, boolean emailVerified, AuthTokens tokens) {}
}

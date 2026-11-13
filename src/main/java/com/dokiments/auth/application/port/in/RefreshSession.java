package com.dokiments.auth.application.port.in;

import com.dokiments.auth.domain.AuthSession;

/** Renews a session's tokens before the ID token runs out. */
public interface RefreshSession {

    /**
     * New tokens, and the profile as it is now, so a role changed since sign-in takes effect. The session still ends
     * when it would have.
     *
     * @throws com.dokiments.shared.error.UnauthenticatedException if Firebase refused the refresh token (revoked, or
     *     the account was disabled): the session must end
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if Firebase couldn't be reached: the session
     *     stays valid, and the client can try again
     */
    AuthSession refresh(AuthSession session);
}

package com.dokiments.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;

/**
 * Works out who sent a request, for the security rules. Implemented by the module that knows how people sign in, so
 * this package needs no knowledge of sessions or Firebase.
 *
 * <p>Called once per request, before the rules are checked.
 */
public interface RequestAuthenticator {

    /**
     * Who the request is from, or {@code null} when it carries no valid session. An implementation may refresh the
     * session on the way, and clears one that is no longer valid, writing the cookies that say so to {@code response}.
     *
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if checking the session needed an external
     *     service that couldn't be reached; the request is then answered with {@code 503}
     */
    @Nullable Authentication authenticate(HttpServletRequest request, HttpServletResponse response);
}

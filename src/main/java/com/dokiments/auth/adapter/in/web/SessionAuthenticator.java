package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.application.port.in.RefreshSession;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.platform.security.RequestAuthenticator;
import com.dokiments.shared.error.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Turns the session cookie into a signed-in user for each request, as the web app's {@code withSession} does.
 *
 * <ul>
 *   <li>No session, or one past its one-day end: not signed in. An ended session is removed and its cookies expired.
 *   <li>An ID token about to run out: refreshed first, and the session stored with the new tokens. If Firebase refuses
 *       the refresh, the session ends. If Firebase can't be reached, the request is answered with {@code 503} and the
 *       session kept, because it is most likely still valid.
 * </ul>
 */
@Component
class SessionAuthenticator implements RequestAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticator.class);

    /**
     * Requests that never refresh: signing in or up replaces the session anyway, and signing out must work even when
     * Firebase can't be reached.
     */
    private static final Set<String> NO_REFRESH =
            Set.of("/api/auth/sign-in", "/api/auth/sign-up", "/api/auth/sign-out");

    private static final List<SimpleGrantedAuthority> SIGNED_IN = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final SessionStore store;
    private final RefreshSession refreshSession;
    private final Clock clock;

    SessionAuthenticator(SessionStore store, RefreshSession refreshSession, Clock clock) {
        this.store = store;
        this.refreshSession = refreshSession;
        this.clock = clock;
    }

    @Override
    public @Nullable Authentication authenticate(HttpServletRequest request, HttpServletResponse response) {
        var stored = store.read(request);
        if (stored.isEmpty()) {
            return null;
        }
        AuthSession session = stored.get();
        var now = clock.instant();
        if (session.isExpired(now)) {
            store.end(request, response);
            return null;
        }
        if (session.needsRefresh(now) && !NO_REFRESH.contains(request.getRequestURI())) {
            try {
                session = refreshSession.refresh(session);
                store.update(request, session);
            } catch (UnauthenticatedException e) {
                log.info("Firebase refused to refresh a session, which ends now", e);
                store.end(request, response);
                return null;
            }
        }
        return signedIn(session);
    }

    static Authentication signedIn(AuthSession session) {
        return UsernamePasswordAuthenticationToken.authenticated(session, null, SIGNED_IN);
    }
}

package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.AuthTokens;
import com.dokiments.users.AuthProvider;
import com.dokiments.users.UserProfile;
import com.dokiments.users.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the signed-in session in the HTTP session (Spring Session, in Redis), behind the HttpOnly
 * {@code dokiments-session} cookie, and maintains the readable {@code dokiments-session-expiry} cookie beside it.
 *
 * <p>The session is stored as one JSON text rather than as Java objects. If a later version stores it differently, an
 * older session simply can't be read and counts as signed out, instead of failing every request that carries it.
 */
@Component
class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    static final String ATTRIBUTE = "dokiments.auth.session";

    /**
     * Readable by the web app's JavaScript, which schedules the sign-out at the session's end. It only holds that time,
     * and decides nothing: the server checks the session itself on every request.
     */
    static final String EXPIRY_COOKIE = "dokiments-session-expiry";

    private final JsonMapper json;
    private final SessionCookies.Settings cookies;

    SessionStore(JsonMapper json, SessionCookies.Settings cookies) {
        this.json = json;
        this.cookies = cookies;
    }

    /** The session of the request, if it has a readable one. */
    Optional<AuthSession> read(HttpServletRequest request) {
        var httpSession = request.getSession(false);
        if (httpSession == null || !(httpSession.getAttribute(ATTRIBUTE) instanceof String stored)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(stored, StoredSession.class).toSession());
        } catch (RuntimeException e) {
            log.info("A stored session couldn't be read and counts as signed out", e);
            return Optional.empty();
        }
    }

    /**
     * Stores a newly signed-in session. The session gets a new ID even if the browser already had one, so an ID someone
     * planted beforehand can't be used to take over the signed-in session.
     */
    void start(HttpServletRequest request, HttpServletResponse response, AuthSession session) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute(ATTRIBUTE, json.writeValueAsString(StoredSession.of(session)));
        writeExpiryCookie(response, String.valueOf(session.expiresAt().toEpochMilli()), AuthSession.MAX_AGE);
    }

    /** Stores a session's new tokens. Its end, and so the expiry cookie, stay as they were. */
    void update(HttpServletRequest request, AuthSession session) {
        var httpSession = request.getSession(false);
        if (httpSession != null) {
            httpSession.setAttribute(ATTRIBUTE, json.writeValueAsString(StoredSession.of(session)));
        }
    }

    /** Signs out: removes the session, which also expires its cookie, and expires the readable cookie. */
    void end(HttpServletRequest request, HttpServletResponse response) {
        var httpSession = request.getSession(false);
        if (httpSession != null) {
            httpSession.invalidate();
        }
        writeExpiryCookie(response, "", Duration.ZERO);
    }

    private void writeExpiryCookie(HttpServletResponse response, String value, Duration maxAge) {
        // Deliberately not HttpOnly: the web app reads it from JavaScript.
        var cookie = ResponseCookie.from(EXPIRY_COOKIE, value)
                .path("/")
                .sameSite("Lax")
                .secure(cookies.secureCookies())
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** The stored form of a session. */
    record StoredSession(
            String uid,
            String email,
            String displayName,
            String role,
            String provider,
            boolean linkedGoogle,
            UserProfile.Details details,
            boolean emailVerified,
            String idToken,
            String refreshToken,
            long idTokenExpiresAt,
            long expiresAt) {

        static StoredSession of(AuthSession session) {
            UserProfile user = session.user();
            return new StoredSession(
                    user.uid(),
                    user.email(),
                    user.displayName(),
                    user.role().value(),
                    user.provider().value(),
                    user.linkedGoogle(),
                    user.details(),
                    session.emailVerified(),
                    session.tokens().idToken(),
                    session.tokens().refreshToken(),
                    session.tokens().idTokenExpiresAt().toEpochMilli(),
                    session.expiresAt().toEpochMilli());
        }

        /**
         * The session, or an exception if the stored text lacks any of its fields: a session stored in another shape
         * must count as signed out, never be half read.
         */
        AuthSession toSession() {
            Stream.of(uid, email, displayName, role, provider, details, idToken, refreshToken)
                    .forEach(field -> Objects.requireNonNull(field, "A stored session lacks a field"));
            var user = new UserProfile(
                    uid,
                    email,
                    displayName,
                    UserRole.fromValue(role),
                    AuthProvider.fromValue(provider),
                    linkedGoogle,
                    details);
            var tokens = new AuthTokens(idToken, refreshToken, Instant.ofEpochMilli(idTokenExpiresAt));
            return new AuthSession(user, emailVerified, tokens, Instant.ofEpochMilli(expiresAt));
        }

        @Override
        public String toString() {
            return "StoredSession[uid=" + uid + "]";
        }
    }
}

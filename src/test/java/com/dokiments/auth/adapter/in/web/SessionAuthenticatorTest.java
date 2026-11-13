package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.auth.TestUsers;
import com.dokiments.auth.application.port.in.RefreshSession;
import com.dokiments.auth.domain.AuthSession;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.shared.error.UpstreamUnavailableException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** What happens to a session whose refresh fails, which the Auth emulator can't simulate. */
class SessionAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final SessionStore store =
            new SessionStore(JsonMapper.builder().build(), new SessionCookies.Settings(true));
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void firebaseUnreachableDuringARefreshKeepsTheSession() {
        signedInWithATokenAboutToRunOut();
        RefreshSession unreachable = _ -> {
            throw new UpstreamUnavailableException(new ConnectException("refused"));
        };

        assertThatThrownBy(() -> authenticator(unreachable).authenticate(request, response))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThat(store.read(request)).isPresent();
    }

    @Test
    void aRefusedRefreshEndsTheSession() {
        signedInWithATokenAboutToRunOut();
        RefreshSession refused = _ -> {
            throw new UnauthenticatedException("Your session expired. Please sign in again.");
        };

        assertThat(authenticator(refused).authenticate(request, response)).isNull();
        assertThat(store.read(request)).isEmpty();
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("dokiments-session-expiry=;"));
    }

    @Test
    void signingOutNeverWaitsForARefresh() {
        signedInWithATokenAboutToRunOut();
        var signOut = new MockHttpServletRequest("POST", "/api/auth/sign-out");
        signOut.setSession(request.getSession());
        RefreshSession mustNotBeCalled = _ -> {
            throw new AssertionError("refreshed while signing out");
        };

        assertThat(authenticator(mustNotBeCalled).authenticate(signOut, response))
                .isNotNull();
    }

    private void signedInWithATokenAboutToRunOut() {
        AuthSession session = TestUsers.session(NOW.minus(Duration.ofHours(2)), NOW.plusSeconds(10));
        store.start(request, new MockHttpServletResponse(), session);
    }

    private SessionAuthenticator authenticator(RefreshSession refreshSession) {
        return new SessionAuthenticator(store, refreshSession, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}

package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.auth.TestUsers;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** The stored session and the readable expiry cookie. */
class SessionStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/sign-in");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void aStoredSessionReadsBackAsItWas() {
        var store = store(true);
        var session = TestUsers.session(NOW, NOW.plusSeconds(3600));

        store.start(request, response, session);

        assertThat(store.read(request)).contains(session);
    }

    @Test
    void theExpiryCookieIsSecureWhereCookiesAre() {
        store(true).start(request, response, TestUsers.session(NOW, NOW.plusSeconds(3600)));

        assertThat(response.getHeader("Set-Cookie")).contains("Secure");
    }

    @Test
    void locallyTheExpiryCookieWorksOverPlainHttp() {
        store(false).start(request, response, TestUsers.session(NOW, NOW.plusSeconds(3600)));

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("dokiments-session-expiry=" + NOW.plusSeconds(86400).toEpochMilli())
                .doesNotContain("Secure");
    }

    @Test
    void aSessionMissingTextFieldsCountsAsSignedOut() {
        request.getSession(true)
                .setAttribute(
                        SessionStore.ATTRIBUTE,
                        "{\"uid\":\"u\",\"emailVerified\":false,\"linkedGoogle\":false,\"idTokenExpiresAt\":1,"
                                + "\"expiresAt\":1}");

        assertThat(store(true).read(request)).isEmpty();
    }

    @Test
    void aSessionStoredInAnotherShapeCountsAsSignedOut() {
        request.getSession(true).setAttribute(SessionStore.ATTRIBUTE, "{\"something\":\"else\"}");

        assertThat(store(true).read(request)).isEmpty();
    }

    private static SessionStore store(boolean secureCookies) {
        return new SessionStore(JsonMapper.builder().build(), new SessionCookies.Settings(secureCookies));
    }
}

package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Signing up, in and out end to end, as the web app does it: through the real security setup, with the session in
 * Redis, accounts in the Auth emulator and profiles in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuthIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @Autowired
    private RedisSessionRepository sessions;

    // Signing up

    @Test
    void signingUpCreatesTheAccountItsProfileAndASession() throws Exception {
        String email = newAddress();

        MvcTestResult result = signUp("Jane Doe", email, "secret123");

        assertThat(result).hasStatusOk();
        var body = JSON.readTree(result.getResponse().getContentAsByteArray()).path("user");
        assertThat(body.path("email").asString()).isEqualTo(email);
        assertThat(body.path("displayName").asString()).isEqualTo("Jane Doe");
        assertThat(body.path("role").asString()).isEqualTo("free");
        assertThat(body.path("provider").asString()).isEqualTo("password");
        assertThat(body.path("emailVerified").asBoolean(true)).isFalse();
        assertThat(body.path("linkedGoogle").asBoolean(true)).isFalse();
        assertThat(body.propertyNames())
                .containsExactlyInAnyOrder(
                        "uid", "email", "displayName", "emailVerified", "provider", "role", "linkedGoogle");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("idToken", "refreshToken", "promo");

        var profile = FirestoreCalls.await(firestore
                .collection("users")
                .document(body.path("uid").asString())
                .get());
        assertThat(profile.getString("role")).isEqualTo("free");
        assertThat(profile.getString("provider")).isEqualTo("password");
        assertThat(profile.getString("email")).isEqualTo(email);
        assertThat(profile.getString("displayName")).isEqualTo("Jane Doe");
        assertThat(profile.getTimestamp("createdAt")).isNotNull();
    }

    @Test
    void theSessionCookieIsHttpOnlyAndTheExpiryCookieIsReadable() {
        MvcTestResult result = signUp("Jane", newAddress(), "secret123");

        String session = setCookie(result, "dokiments-session");
        assertThat(session).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=86400");

        String expiry = setCookie(result, "dokiments-session-expiry");
        assertThat(expiry).contains("SameSite=Lax", "Path=/", "Max-Age=86400").doesNotContain("HttpOnly");
        long expiresAt = Long.parseLong(expiry.substring(expiry.indexOf('=') + 1, expiry.indexOf(';')));
        assertThat(Instant.ofEpochMilli(expiresAt))
                .isCloseTo(Instant.now().plus(Duration.ofDays(1)), within(Duration.ofMinutes(1)));
    }

    @Test
    void anAddressAlreadyInUseIsRefusedWithTheWebAppsMessage() {
        String email = newAddress();
        signUp("Jane", email, "secret123");

        assertThat(signUp("Jane again", email, "secret123"))
                .hasStatus(401)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"An account already exists for this email.\"}");
    }

    @Test
    void aWeakPasswordIsRefusedWithTheWebAppsMessage() {
        assertThat(signUp("Jane", newAddress(), "123"))
                .hasStatus(401)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Use a stronger password with at least 6 characters.\"}");
    }

    @Test
    void anInvalidBodyIsRefusedBeforeFirebaseIsAsked() {
        assertThat(post(
                        "/api/auth/sign-up",
                        "{\"displayName\":\"Jane\",\"email\":\"jane@example\",\"password\":\"x\"}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
        assertThat(post("/api/auth/sign-in", "{\"email\":\"jane@example.com\"}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    // Signing in, me and signing out

    @Test
    void aWrongPasswordIsRefusedWithTheWebAppsMessage() {
        String email = newAddress();
        signUp("Jane", email, "secret123");

        assertThat(signIn(email, "wrong-password"))
                .hasStatus(401)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"The email or password is incorrect.\"}");
    }

    @Test
    void meAnswersWithTheSignedInUser() {
        String email = newAddress();
        signUp("Jane", email, "secret123");
        MvcTestResult signedIn = signIn(email, "secret123");

        MvcTestResult me = me(cookie(signedIn, "dokiments-session"));

        assertThat(me).hasStatusOk().bodyJson().extractingPath("$.user.email").isEqualTo(email);
    }

    @Test
    void meWithoutASessionIsUnauthorisedWithANullBody() {
        assertThat(me(null)).hasStatus(401).hasBodyTextEqualTo("null");
    }

    @Test
    void signingOutEndsTheSessionAndExpiresBothCookies() {
        String email = newAddress();
        MvcTestResult signedUp = signUp("Jane", email, "secret123");
        Cookie session = cookie(signedUp, "dokiments-session");

        MvcTestResult signedOut = post("/api/auth/sign-out", "", session);

        assertThat(signedOut).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"ok\":true}");
        assertThat(setCookie(signedOut, "dokiments-session")).contains("Max-Age=0");
        assertThat(setCookie(signedOut, "dokiments-session-expiry")).contains("Max-Age=0");
        assertThat(me(session)).hasStatus(401).hasBodyTextEqualTo("null");
    }

    @Test
    void signingOutWithoutASessionStillSucceeds() {
        assertThat(post("/api/auth/sign-out", "")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"ok\":true}");
    }

    @Test
    void signingInGivesANewSessionIdEvenWhenTheBrowserAlreadyHadOne() {
        String email = newAddress();
        Cookie first = cookie(signUp("Jane", email, "secret123"), "dokiments-session");

        Cookie second = cookie(signIn(email, "secret123", first), "dokiments-session");

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        assertThat(sessions.findById(sessionId(first))).isNull();
    }

    // CSRF

    @Test
    void aChangeWithoutTheCsrfTokenIsForbidden() {
        assertThat(mvc.post()
                        .uri("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"secret123\"}"))
                .hasStatus(403)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Forbidden.\"}");
    }

    @Test
    void theCsrfTokenCookieComesWithAnyAnswer() {
        assertThat(setCookie(me(null), "XSRF-TOKEN")).doesNotContain("HttpOnly");
    }

    // The session's lifetime

    @Test
    void aSessionPastItsEndIsSignedOut() {
        Cookie session = cookie(signUp("Jane", newAddress(), "secret123"), "dokiments-session");
        changeStoredSession(
                session,
                stored -> stored.put("expiresAt", Instant.now().minusSeconds(1).toEpochMilli()));

        MvcTestResult me = me(session);

        assertThat(me).hasStatus(401).hasBodyTextEqualTo("null");
        assertThat(setCookie(me, "dokiments-session-expiry")).contains("Max-Age=0");
        assertThat(sessions.findById(sessionId(session))).isNull();
    }

    @Test
    void anIdTokenAboutToRunOutIsRefreshedAndTheSessionKeepsItsEnd() {
        Cookie session = cookie(signUp("Jane", newAddress(), "secret123"), "dokiments-session");
        ObjectNode before = storedSession(session);
        changeStoredSession(
                session, stored -> stored.put("idTokenExpiresAt", Instant.now().toEpochMilli()));

        MvcTestResult refreshed = me(session);

        assertThat(refreshed).hasStatusOk();
        // The same session, still under the same ID: no new session cookie.
        assertThat(refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("dokiments-session"));

        ObjectNode after = storedSession(session);
        // New tokens from Firebase, valid for about an hour again. (The emulator's tokens are built from the time to
        // the second, so the token text itself can come back unchanged.)
        assertThat(after.path("idTokenExpiresAt").asLong())
                .isGreaterThan(Instant.now().plus(Duration.ofMinutes(50)).toEpochMilli());
        assertThat(after.path("expiresAt").asLong())
                .isEqualTo(before.path("expiresAt").asLong());
    }

    @Test
    void aRefreshTokenFirebaseRefusesEndsTheSession() {
        Cookie session = cookie(signUp("Jane", newAddress(), "secret123"), "dokiments-session");
        changeStoredSession(session, stored -> {
            stored.put("refreshToken", "not-a-refresh-token");
            stored.put("idTokenExpiresAt", Instant.now().toEpochMilli());
        });

        assertThat(me(session)).hasStatus(401).hasBodyTextEqualTo("null");
        assertThat(sessions.findById(sessionId(session))).isNull();
    }

    // Helpers

    private MvcTestResult signUp(String displayName, String email, String password) {
        return post(
                "/api/auth/sign-up",
                "{\"displayName\":\"" + displayName + "\",\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\"}");
    }

    private MvcTestResult signIn(String email, String password, Cookie... cookies) {
        return post("/api/auth/sign-in", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", cookies);
    }

    private MvcTestResult me(@Nullable Cookie session) {
        var request = mvc.get().uri("/api/auth/me");
        if (session != null) {
            request = request.cookie(session);
        }
        return request.exchange();
    }

    /** A change as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult post(String path, String body, Cookie... cookies) {
        Cookie csrf = cookie(mvc.get().uri("/api/auth/me").exchange(), "XSRF-TOKEN");
        List<Cookie> all = new ArrayList<>(List.of(cookies));
        all.add(csrf);
        return mvc.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(all.toArray(Cookie[]::new))
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    private static Cookie cookie(MvcTestResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie %s", name).isNotNull();
        return cookie;
    }

    private static String setCookie(MvcTestResult result, String name) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie for " + name));
    }

    /** Spring Session's cookie holds the session ID, Base64-encoded. */
    private static String sessionId(Cookie session) {
        return new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
    }

    private ObjectNode storedSession(Cookie cookie) {
        Session session = sessions.findById(sessionId(cookie));
        assertThat(session).isNotNull();
        String stored = session.getAttribute(SessionStore.ATTRIBUTE);
        return (ObjectNode) JSON.readTree(stored);
    }

    /** Changes the stored session directly, to simulate time passing. */
    private void changeStoredSession(Cookie cookie, Consumer<ObjectNode> change) {
        Session session = sessions.findById(sessionId(cookie));
        assertThat(session).isNotNull();
        ObjectNode stored = (ObjectNode) JSON.readTree(session.<String>getAttribute(SessionStore.ATTRIBUTE));
        change.accept(stored);
        session.setAttribute(SessionStore.ATTRIBUTE, JSON.writeValueAsString(stored));
        save(session);
    }

    // Spring Session's session class is package-private, so the repository is used through its raw interface.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void save(Session session) {
        ((SessionRepository) sessions).save(session);
    }

    private static String newAddress() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}

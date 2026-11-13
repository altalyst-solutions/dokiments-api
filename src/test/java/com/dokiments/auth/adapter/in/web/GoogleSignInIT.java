package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.Firestore;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google sign-in end to end. Google itself is replaced by a stand-in for its token endpoint, which hands out unsigned
 * Google ID tokens; the Auth emulator accepts those, so everything after Google (Firebase, the session, the profile)
 * runs for real.
 */
@IntegrationTest
@AutoConfigureMockMvc
class GoogleSignInIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static HttpServer google;

    /** Who the stand-in says signed in with the next code, as {sub, email}. */
    private static volatile String[] nextGoogleUser = {"google-sub", "someone@example.com"};

    private static final List<String> tokenRequests = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @BeforeAll
    static void startGoogle() throws IOException {
        google = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        google.createContext("/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(URLDecoder.decode(form, StandardCharsets.UTF_8));
            byte[] body;
            int status;
            if (form.contains("code=refused")) {
                status = 400;
                body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = JSON.writeValueAsBytes(Map.of("id_token", unsignedGoogleToken(nextGoogleUser)));
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        google.start();
    }

    @AfterAll
    static void stopGoogle() {
        google.stop(0);
    }

    @DynamicPropertySource
    static void googleSettings(DynamicPropertyRegistry registry) {
        registry.add("dokiments.auth.google.client-id", () -> "test-client");
        registry.add("dokiments.auth.google.client-secret", () -> "test-secret");
        registry.add(
                "dokiments.auth.google.token-url",
                () -> "http://127.0.0.1:" + google.getAddress().getPort() + "/token");
    }

    // The start

    @Test
    void theStartSendsTheBrowserToGoogleWithAStateCookie() {
        MvcTestResult started =
                mvc.get().uri("/api/auth/google/start?next=/my-documents").exchange();

        assertThat(started).hasStatus(302);
        var location = UriComponentsBuilder.fromUriString(location(started)).build();
        assertThat(location.getHost()).isEqualTo("accounts.google.com");
        var query = location.getQueryParams();
        assertThat(query.getFirst("client_id")).isEqualTo("test-client");
        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(param(query, "scope")).isEqualTo("openid email profile");
        assertThat(query.getFirst("prompt")).isEqualTo("select_account");
        assertThat(param(query, "redirect_uri")).isEqualTo("https://dokiments.com/api/auth/google/callback");

        String cookie = stateCookie(started);
        assertThat(cookie)
                .contains("HttpOnly", "SameSite=Lax", "Path=/api/auth/google", "Max-Age=600")
                .startsWith(GoogleSignInController.STATE_COOKIE + "=" + query.getFirst("state") + ";");
    }

    // The callback

    @Test
    void aFirstGoogleSignInCreatesTheAccountAndLandsWhereAsked() {
        String email = newAddress();
        nextGoogleUser = new String[] {"sub-" + UUID.randomUUID(), email};
        String state = start("/my-documents?tab=shared");

        MvcTestResult landed = callback("the-code", state, state);

        assertThat(landed).hasStatus(302);
        assertThat(landed.getResponse().getRedirectedUrl()).isEqualTo("/my-documents?tab=shared");
        assertThat(stateCookie(landed)).contains("Max-Age=0");
        Cookie session = landed.getResponse().getCookie(SessionCookies.NAME);
        assertThat(session).isNotNull();

        var user = JSON.readTree(mvc.get()
                        .uri("/api/auth/me")
                        .cookie(session)
                        .exchange()
                        .getResponse()
                        .getContentAsByteArray())
                .path("user");
        assertThat(user.path("email").asString()).isEqualTo(email);
        assertThat(user.path("provider").asString()).isEqualTo("google");
        assertThat(user.path("emailVerified").asBoolean(false)).isTrue();
        var profile = FirestoreCalls.await(firestore
                .collection("users")
                .document(user.path("uid").asString())
                .get());
        assertThat(profile.getString("provider")).isEqualTo("google");
        assertThat(profile.getString("role")).isEqualTo("free");

        assertThat(tokenRequests.getLast())
                .contains(
                        "code=the-code",
                        "client_id=test-client",
                        "client_secret=test-secret",
                        "grant_type=authorization_code",
                        "redirect_uri=https://dokiments.com/api/auth/google/callback");
    }

    @Test
    void aGoogleSignInWithAPasswordAccountsAddressSignsInToThatAccountAndMarksItLinked() {
        String email = newAddress();
        MvcTestResult signedUp =
                signUp("{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\"}");
        String uid = JSON.readTree(signedUp.getResponse().getContentAsByteArray())
                .path("user")
                .path("uid")
                .asString();
        nextGoogleUser = new String[] {"sub-" + UUID.randomUUID(), email};
        String state = start(null);

        MvcTestResult landed = callback("the-code", state, state);

        assertThat(landed.getResponse().getRedirectedUrl()).isEqualTo("/dashboard");
        Cookie session = landed.getResponse().getCookie(SessionCookies.NAME);
        assertThat(session).isNotNull();
        var user = JSON.readTree(mvc.get()
                        .uri("/api/auth/me")
                        .cookie(session)
                        .exchange()
                        .getResponse()
                        .getContentAsByteArray())
                .path("user");
        assertThat(user.path("uid").asString()).isEqualTo(uid);
        assertThat(user.path("provider").asString()).isEqualTo("password");
        assertThat(user.path("linkedGoogle").asBoolean(false)).isTrue();
    }

    @Test
    void aReturnPathOffThisSiteLandsOnTheDashboard() {
        nextGoogleUser = new String[] {"sub-" + UUID.randomUUID(), newAddress()};
        String state = start("//evil.example/steal");

        assertThat(callback("the-code", state, state).getResponse().getRedirectedUrl())
                .isEqualTo("/dashboard");
    }

    @Test
    void aStateThatDoesntMatchTheCookieIsRefused() {
        String state = start(null);

        assertSignInFailed(callback("the-code", "another-state", state));
        assertSignInFailed(callback("the-code", state, null));
    }

    @Test
    void aStateCanOnlyBeUsedOnce() {
        nextGoogleUser = new String[] {"sub-" + UUID.randomUUID(), newAddress()};
        String state = start(null);
        callback("the-code", state, state);

        assertSignInFailed(callback("the-code", state, state));
    }

    @Test
    void cancellingAtGoogleOrACodeGoogleRefusesLandsOnTheSignInPage() {
        String state = start(null);

        assertSignInFailed(mvc.get()
                .uri("/api/auth/google/callback?error=access_denied&state=" + state)
                .cookie(new Cookie(GoogleSignInController.STATE_COOKIE, state))
                .exchange());
        String another = start(null);
        assertSignInFailed(callback("refused", another, another));
    }

    // Helpers

    /** Starts a sign-in and returns its state, as the browser would carry it back. */
    private String start(@Nullable String next) {
        var uri = next == null ? "/api/auth/google/start" : "/api/auth/google/start?next=" + next;
        var started = mvc.get().uri(uri).exchange();
        return UriComponentsBuilder.fromUriString(location(started))
                .build()
                .getQueryParams()
                .getFirst("state");
    }

    private MvcTestResult callback(String code, String state, @Nullable String stateCookie) {
        var request = mvc.get().uri("/api/auth/google/callback?code=" + code + "&state=" + state);
        if (stateCookie != null) {
            request = request.cookie(new Cookie(GoogleSignInController.STATE_COOKIE, stateCookie));
        }
        return request.exchange();
    }

    private static void assertSignInFailed(MvcTestResult result) {
        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/sign-in?error=google_failed");
        assertThat(result.getResponse().getCookie(SessionCookies.NAME)).isNull();
    }

    private MvcTestResult signUp(String body) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return mvc.post()
                .uri("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    /** The state cookie's whole {@code Set-Cookie} header, with its attributes. */
    private static String stateCookie(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(GoogleSignInController.STATE_COOKIE + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie for the state cookie"));
    }

    private static String location(MvcTestResult result) {
        return Objects.requireNonNull(result.getResponse().getRedirectedUrl(), "Not a redirect");
    }

    private static String param(MultiValueMap<String, String> query, String name) {
        return URLDecoder.decode(Objects.requireNonNull(query.getFirst(name), name), StandardCharsets.UTF_8);
    }

    /** A Google ID token as the Auth emulator accepts it: unsigned, with the claims that matter. */
    private static String unsignedGoogleToken(String[] user) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encoder.encodeToString(JSON.writeValueAsBytes(
                Map.of("sub", user[0], "email", user[1], "email_verified", true, "name", "Gee")));
        return header + "." + claims + ".";
    }

    private static String newAddress() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}

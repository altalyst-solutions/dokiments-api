package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.FirebaseEmulatorsContainer;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Changing one's own account end to end, as the web app does it: through the real security setup, with the session in
 * Redis, accounts in the Auth emulator and profiles in the Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AccountIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String UNAUTHORISED = "{\"error\":\"Unauthorised.\"}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @Autowired
    private FirebaseEmulatorsContainer emulators;

    // Every route but the reset needs a session

    @Test
    void theAccountRoutesNeedASession() {
        for (String path : List.of(
                "/api/auth/update-password",
                "/api/auth/reauthenticate",
                "/api/auth/update-profile",
                "/api/auth/send-verification",
                "/api/auth/refresh-verification")) {
            assertThat(post(path, "{}", null))
                    .as(path)
                    .hasStatus(401)
                    .bodyJson()
                    .isStrictlyEqualTo(UNAUTHORISED);
        }
    }

    // Passwords

    @Test
    void aNewPasswordReplacesTheOldOne() {
        Account jane = signUp();

        assertThat(post("/api/auth/update-password", "{\"password\":\"new-secret\"}", jane.session()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"ok\":true}");
        assertThat(signIn(jane.email(), "new-secret")).hasStatusOk();
        assertThat(signIn(jane.email(), "secret123")).hasStatus(401);
    }

    @Test
    void theSessionStillWorksAfterAPasswordChange() {
        Account jane = signUp();
        post("/api/auth/update-password", "{\"password\":\"new-secret\"}", jane.session());

        assertThat(mvc.get().uri("/api/auth/me").cookie(jane.session())).hasStatusOk();
    }

    @Test
    void aWeakNewPasswordIsRefusedWithFirebasesMessage() {
        assertThat(post("/api/auth/update-password", "{\"password\":\"123\"}", signUp().session()))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Use a stronger password with at least 6 characters.\"}");
    }

    @Test
    void anEmptyOrOverlongNewPasswordIsAnInvalidBody() {
        Cookie session = signUp().session();

        assertThat(post("/api/auth/update-password", "{\"password\":\"\"}", session))
                .hasStatus(400);
        assertThat(post("/api/auth/update-password", "{\"password\":\"" + "p".repeat(129) + "\"}", session))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    @Test
    void reauthenticatingChecksTheSessionsOwnPassword() {
        Account jane = signUp();

        assertThat(post("/api/auth/reauthenticate", "{\"password\":\"secret123\"}", jane.session()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"ok\":true}");
        assertThat(post("/api/auth/reauthenticate", "{\"password\":\"wrong\"}", jane.session()))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"The email or password is incorrect.\"}");
    }

    @Test
    void aResetLinkIsSentForAKnownAddress() {
        Account jane = signUp();

        assertThat(post("/api/auth/forgot-password", "{\"email\":\" " + jane.email() + " \"}", null))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"ok\":true}");
        assertThat(oobCodes(jane.email(), "PASSWORD_RESET")).isNotEmpty();
    }

    @Test
    void anImplausibleAddressForAResetIsAnInvalidBody() {
        assertThat(post("/api/auth/forgot-password", "{\"email\":\"jane@example\"}", null))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    // The profile

    @Test
    void aProfileChangeIsStoredAndAnsweredAsTheWebAppDoes() {
        Account jane = signUp();

        MvcTestResult result = post(
                "/api/auth/update-profile",
                "{\"displayName\":\" Jane Doe \",\"companyName\":\"Acme\",\"businessEmail\":\"billing@acme.test\"}",
                jane.session());

        assertThat(result).hasStatusOk();
        var user = JSON.readTree(result.getResponse().getContentAsByteArray()).path("user");
        assertThat(user.path("displayName").asString()).isEqualTo("Jane Doe");
        assertThat(user.path("companyName").asString()).isEqualTo("Acme");
        assertThat(user.path("businessEmail").asString()).isEqualTo("billing@acme.test");
        // Fields that weren't sent are cleared, and answered as empty text.
        assertThat(user.path("address").isString()).isTrue();
        assertThat(user.path("address").asString()).isEmpty();

        var stored = FirestoreCalls.await(
                firestore.collection("users").document(jane.uid()).get());
        assertThat(stored.getString("displayName")).isEqualTo("Jane Doe");
        assertThat(stored.getString("companyName")).isEqualTo("Acme");
        assertThat(stored.getString("address")).isEmpty();
        assertThat(stored.getString("role")).isEqualTo("free");
        assertThat(stored.getTimestamp("updatedAt")).isNotNull();

        assertThat(mvc.get().uri("/api/auth/me").cookie(jane.session()))
                .bodyJson()
                .extractingPath("$.user.displayName")
                .isEqualTo("Jane Doe");
    }

    @Test
    void anInvalidProfileIsAnInvalidBody() {
        Cookie session = signUp().session();

        for (String body : List.of(
                "{\"displayName\":\"   \"}",
                "{\"displayName\":\"Jane\",\"businessEmail\":\"not-an-address\"}",
                "{\"displayName\":\"Jane\",\"phone\":\"" + "1".repeat(51) + "\"}",
                // Text fields take text only, as in the web app.
                "{\"displayName\":\"Jane\",\"phone\":12345}")) {
            assertThat(post("/api/auth/update-profile", body, session))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
        }
    }

    // Verification

    @Test
    void aVerificationEmailCanBeSentAgain() {
        Account jane = signUp();
        int before = oobCodes(jane.email(), "VERIFY_EMAIL").size();

        MvcTestResult result = post("/api/auth/send-verification", "", jane.session());

        assertThat(result).hasStatus(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(oobCodes(jane.email(), "VERIFY_EMAIL")).hasSize(before + 1);
    }

    @Test
    void refreshingPicksUpAVerifiedAddressAndThenNoMoreEmailsAreSent() {
        Account jane = signUp();
        verify(oobCodes(jane.email(), "VERIFY_EMAIL").getLast());

        assertThat(post("/api/auth/refresh-verification", "", jane.session()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.emailVerified")
                .isEqualTo(true);

        int before = oobCodes(jane.email(), "VERIFY_EMAIL").size();
        assertThat(post("/api/auth/send-verification", "", jane.session())).hasStatus(204);
        assertThat(oobCodes(jane.email(), "VERIFY_EMAIL")).hasSize(before);
    }

    // Helpers

    private record Account(String uid, String email, Cookie session) {}

    private Account signUp() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcTestResult result = post(
                "/api/auth/sign-up",
                "{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\"}",
                null);
        assertThat(result).hasStatusOk();
        String uid = JSON.readTree(result.getResponse().getContentAsByteArray())
                .path("user")
                .path("uid")
                .asString();
        Cookie session = result.getResponse().getCookie(SessionCookies.NAME);
        assertThat(session).isNotNull();
        return new Account(uid, email, session);
    }

    private MvcTestResult signIn(String email, String password) {
        return post("/api/auth/sign-in", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
    }

    /** A change as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult post(String path, String body, @Nullable Cookie session) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        List<Cookie> cookies = new ArrayList<>(List.of(csrf));
        if (session != null) {
            cookies.add(session);
        }
        return mvc.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies.toArray(Cookie[]::new))
                .header("X-XSRF-TOKEN", csrf.getValue())
                .exchange();
    }

    /** The emails the Auth emulator would have sent to {@code email}, by kind. */
    private List<String> oobCodes(String email, String requestType) {
        String answer = emulatorCall("GET", "/emulator/v1/projects/demo-dokiments/oobCodes", null);
        List<String> codes = new ArrayList<>();
        for (var code : JSON.readTree(answer).path("oobCodes")) {
            if (code.path("email").asString().equalsIgnoreCase(email)
                    && code.path("requestType").asString().equals(requestType)) {
                codes.add(code.path("oobCode").asString());
            }
        }
        return codes;
    }

    /** Opens the verification link, as the user would from the email. */
    private void verify(String oobCode) {
        emulatorCall(
                "POST",
                "/identitytoolkit.googleapis.com/v1/accounts:update?key=emulator",
                "{\"oobCode\":\"" + oobCode + "\"}");
    }

    // The Auth emulator only speaks plain HTTP, inside the test container.
    @SuppressWarnings("HttpUrlsUsage")
    private String emulatorCall(String method, String path, @Nullable String body) {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://" + emulators.authEndpoint() + path))
                    .header("Content-Type", "application/json")
                    .method(
                            method,
                            body == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .as("%s %s: %s", method, path, response.body())
                    .isEqualTo(200);
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

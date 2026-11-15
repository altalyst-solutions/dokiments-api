package com.dokiments.promo.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Promo codes end to end, as the web app uses them: redeemed from the settings, or given while signing up or in.
 * Through the real security setup, with sessions in Redis, accounts in the Auth emulator and redemptions in the
 * Firestore emulator.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PromoIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SESSION_COOKIE = "dokiments-session";
    private static final String CODE = "ViSHDOK2026!";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    // The routes

    @Test
    void theRoutesNeedASession() {
        for (var call : List.of(
                call(HttpMethod.GET, "/api/promo-redemptions", null, null),
                call(HttpMethod.POST, "/api/promo-redemptions", "{\"promoCode\":\"" + CODE + "\"}", null))) {
            assertThat(call).hasStatus(401).bodyJson().isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
        }
    }

    @Test
    void aCodeIsRedeemedOnceWithThePromotionsOwnCode() {
        var jane = signUp();
        assertThat(call(HttpMethod.GET, "/api/promo-redemptions", null, jane.session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"redemptions\":[]}");

        MvcTestResult redeemed = redeem(" vish dok2026! ", jane.session());

        assertThat(redeemed).hasStatus(201);
        long redeemedAt = body(redeemed).path("redemption").path("redeemedAt").asLong();
        assertThat(redeemed)
                .bodyJson()
                .isStrictlyEqualTo("{\"redemption\":{\"code\":\"" + CODE + "\",\"promoId\":\"launch-2026\","
                        + "\"redeemedAt\":" + redeemedAt + "}}");
        var stored = FirestoreCalls.await(
                redemptions(jane.uid()).document("launch-2026").get());
        assertThat(stored.getString("code")).isEqualTo(CODE);
        assertThat(FirestoreCalls.toInstant(Objects.requireNonNull(stored.getTimestamp("redeemedAt")))
                        .toEpochMilli())
                .isEqualTo(redeemedAt);
        assertThat(call(HttpMethod.GET, "/api/promo-redemptions", null, jane.session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"redemptions\":[" + body(redeemed).path("redemption") + "]}");

        assertThat(redeem(CODE, jane.session()))
                .hasStatus(409)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"This promo code has already been used for your account.\"}");
    }

    @Test
    void twoRedemptionsAtOnceCantBothSucceed() throws Exception {
        var jane = signUp();

        List<Callable<Integer>> redemptions = List.of(
                () -> redeem(CODE, jane.session()).getResponse().getStatus(),
                () -> redeem(CODE, jane.session()).getResponse().getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (var answer : threads.invokeAll(redemptions)) {
                statuses.add(answer.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void aWrongCodeAndAnInvalidBodyAreTold() {
        Cookie session = signUp().session();

        for (String code : List.of("   ", "VISHDOK2025!")) {
            assertThat(redeem(code, session))
                    .as(code)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid promo code.\"}");
        }
        for (String body : List.of(
                "{}", "{\"promoCode\":\"\"}", "{\"promoCode\":5}", "{\"promoCode\":\"" + "x".repeat(65) + "\"}")) {
            assertThat(call(HttpMethod.POST, "/api/promo-redemptions", body, session))
                    .as(body)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
        }
    }

    @Test
    void anOlderRedemptionWithMissingFieldsIsReadAsEmpty() {
        var jane = signUp();
        FirestoreCalls.await(redemptions(jane.uid()).document("launch-2025").set(Map.of("other", true)));

        assertThat(call(HttpMethod.GET, "/api/promo-redemptions", null, jane.session()))
                .bodyJson()
                .isStrictlyEqualTo("{\"redemptions\":[{\"code\":\"\",\"promoId\":\"launch-2025\",\"redeemedAt\":0}]}");
    }

    // At sign-up and sign-in

    @Test
    void aCodeGivenAtSignUpIsRedeemedAndSigningInWithItAgainSaysSo() {
        String email = newAddress();

        MvcTestResult signedUp = signUp(email, CODE);
        assertThat(body(signedUp).path("promo").asString()).isEqualTo("applied");
        String uid = body(signedUp).path("user").path("uid").asString();
        assertThat(FirestoreCalls.await(redemptions(uid).document("launch-2026").get())
                        .exists())
                .isTrue();

        assertThat(body(signIn(email, "secret123", CODE)).path("promo").asString())
                .isEqualTo("already_redeemed");
        assertThat(body(signIn(email, "secret123", "nope")).path("promo").asString())
                .isEqualTo("invalid");
        assertThat(body(signIn(email, "secret123", "  ")).has("promo")).isFalse();
        assertThat(body(signIn(email, "secret123", null)).has("promo")).isFalse();
    }

    @Test
    void aCodeIsNeverRedeemedForAnAccountTheCallerHasntProven() {
        String email = newAddress();
        String uid = body(signUp(email, null)).path("user").path("uid").asString();

        assertThat(signIn(email, "wrong-password", CODE)).hasStatus(401);

        assertThat(FirestoreCalls.await(redemptions(uid).get()).isEmpty()).isTrue();
    }

    @Test
    void aTooLongCodeAtSignInIsAnInvalidBody() {
        assertThat(signUp(newAddress(), "x".repeat(65)))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Invalid request body.\"}");
    }

    // Helpers

    private record Account(String uid, Cookie session) {}

    /** A new account, signed up without a promo code. */
    private Account signUp() {
        MvcTestResult result = signUp(newAddress(), null);
        assertThat(result).hasStatusOk();
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return new Account(body(result).path("user").path("uid").asString(), session);
    }

    private MvcTestResult signUp(String email, @Nullable String promoCode) {
        return call(
                HttpMethod.POST,
                "/api/auth/sign-up",
                "{\"displayName\":\"Jane\",\"email\":\"" + email + "\",\"password\":\"secret123\""
                        + (promoCode == null ? "" : ",\"promoCode\":\"" + promoCode + "\"") + "}",
                null);
    }

    private MvcTestResult signIn(String email, String password, @Nullable String promoCode) {
        return call(
                HttpMethod.POST,
                "/api/auth/sign-in",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\""
                        + (promoCode == null ? "" : ",\"promoCode\":\"" + promoCode + "\"") + "}",
                null);
    }

    private MvcTestResult redeem(String code, Cookie session) {
        return call(HttpMethod.POST, "/api/promo-redemptions", "{\"promoCode\":\"" + code + "\"}", session);
    }

    /** A request as the web app sends it: with the CSRF token from the cookie, in the header. */
    private MvcTestResult call(HttpMethod method, String path, @Nullable String body, @Nullable Cookie session) {
        Cookie csrf = mvc.get().uri("/api/auth/me").exchange().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        List<Cookie> cookies = new ArrayList<>(List.of(csrf));
        if (session != null) {
            cookies.add(session);
        }
        var request = mvc.method(method)
                .uri(path)
                .cookie(cookies.toArray(Cookie[]::new))
                .header("X-XSRF-TOKEN", csrf.getValue());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static JsonNode body(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private CollectionReference redemptions(String uid) {
        return firestore.collection("users").document(uid).collection("promoRedemptions");
    }

    private static String newAddress() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}

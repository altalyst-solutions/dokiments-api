package com.dokiments.auth.adapter.out.firebase;

import com.dokiments.auth.application.port.out.IdentityProvider;
import com.dokiments.auth.domain.AuthTokens;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase Auth's REST APIs: Identity Toolkit for accounts, Secure Token for refreshes. The Admin SDK can't check a
 * password, which is why these are called directly.
 *
 * <p>Calls go through the application's HTTP client rules, so an unreachable or failing Firebase is reported as
 * unavailable on its own. A refusal ({@code 4xx}) becomes a {@link Refused}, with Firebase's error code and the message
 * the web app shows for it.
 */
class FirebaseIdentityToolkit implements IdentityProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient identityToolkit;
    private final RestClient secureToken;
    private final Clock clock;
    private final String continueUrl;

    FirebaseIdentityToolkit(
            FirebaseAuthProperties properties, RestClient.Builder builder, Clock clock, String continueUrl) {
        // The key goes in a header, never in the address: addresses end up in logs.
        this.identityToolkit = builder.clone()
                .baseUrl(properties.identityToolkitUrl())
                .defaultHeader("X-Goog-Api-Key", properties.keyForRequests())
                .build();
        this.secureToken = builder.clone()
                .baseUrl(properties.secureTokenUrl())
                .defaultHeader("X-Goog-Api-Key", properties.keyForRequests())
                .build();
        this.clock = clock;
        this.continueUrl = continueUrl;
    }

    @Override
    public SignedIn signUp(String displayName, String email, String password) {
        return signedIn(post(
                identityToolkit,
                "/v1/accounts:signUp",
                Map.of("email", email, "password", password, "displayName", displayName, "returnSecureToken", true),
                AccountResponse.class));
    }

    @Override
    public SignedIn signIn(String email, String password) {
        return signedIn(post(
                identityToolkit,
                "/v1/accounts:signInWithPassword",
                Map.of("email", email, "password", password, "returnSecureToken", true),
                AccountResponse.class));
    }

    @Override
    public Refreshed refresh(String refreshToken) {
        // Secure Token's fields are in snake case, unlike Identity Toolkit's.
        var response = post(
                secureToken,
                "/v1/token",
                Map.of("grant_type", "refresh_token", "refresh_token", refreshToken),
                RefreshResponse.class);
        return new Refreshed(
                response.userId(),
                emailVerified(response.idToken()),
                tokens(response.idToken(), response.refreshToken(), response.expiresIn()));
    }

    @Override
    public void sendPasswordResetEmail(String email) {
        post(
                identityToolkit,
                "/v1/accounts:sendOobCode",
                Map.of("requestType", "PASSWORD_RESET", "email", email),
                JsonNode.class);
    }

    @Override
    public Updated changePassword(String idToken, String newPassword) {
        return updated(post(
                identityToolkit,
                "/v1/accounts:update",
                Map.of("idToken", idToken, "password", newPassword, "returnSecureToken", true),
                UpdateResponse.class));
    }

    @Override
    public Updated changeDisplayName(String idToken, String displayName) {
        return updated(post(
                identityToolkit,
                "/v1/accounts:update",
                Map.of("idToken", idToken, "displayName", displayName, "returnSecureToken", true),
                UpdateResponse.class));
    }

    /** Firebase only sends new tokens when the change needs them, such as a new password. */
    private Updated updated(UpdateResponse response) {
        if (response.idToken() == null || response.refreshToken() == null || response.expiresIn() == null) {
            return new Updated(null, null);
        }
        return new Updated(
                tokens(response.idToken(), response.refreshToken(), response.expiresIn()),
                emailVerified(response.idToken()));
    }

    @Override
    public void sendVerificationEmail(String idToken) {
        post(
                identityToolkit,
                "/v1/accounts:sendOobCode",
                Map.of("requestType", "VERIFY_EMAIL", "idToken", idToken, "continueUrl", continueUrl),
                JsonNode.class);
    }

    private <T> T post(RestClient client, String path, Object body, Class<T> type) {
        T response = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (_, refusal) -> {
                    throw refused(refusal);
                })
                .body(type);
        if (response == null) {
            throw new IllegalStateException("Firebase Auth answered " + path + " without a body");
        }
        return response;
    }

    private SignedIn signedIn(AccountResponse response) {
        return new SignedIn(
                response.localId(),
                response.email(),
                response.displayName() == null || response.displayName().isBlank() ? null : response.displayName(),
                emailVerified(response.idToken()),
                tokens(response.idToken(), response.refreshToken(), response.expiresIn()));
    }

    private AuthTokens tokens(String idToken, String refreshToken, String expiresInSeconds) {
        return new AuthTokens(
                idToken, refreshToken, clock.instant().plus(Duration.ofSeconds(Long.parseLong(expiresInSeconds))));
    }

    /** Firebase's own error code, such as {@code INVALID_LOGIN_CREDENTIALS}, with the message for the user. */
    static Refused refused(ClientHttpResponse refusal) {
        String code = null;
        try {
            code = JSON.readTree(refusal.getBody())
                    .path("error")
                    .path("message")
                    .asString(null);
        } catch (RuntimeException | IOException e) {
            // An unreadable refusal still is one; the generic message applies.
        }
        return new Refused(code == null ? "(no code)" : code, FirebaseErrors.messageFor(code));
    }

    /**
     * Whether the ID token says the address is confirmed ({@code email_verified}). The token came straight from
     * Firebase over HTTPS in answer to this call, so its claims are read without checking its signature.
     */
    static boolean emailVerified(String idToken) {
        // A JWT is header.claims.signature; only the claims are read.
        int start = idToken.indexOf('.');
        int end = start < 0 ? -1 : idToken.indexOf('.', start + 1);
        if (end < 0) {
            return false;
        }
        try {
            String claims = new String(
                    Base64.getUrlDecoder().decode(idToken.substring(start + 1, end)), StandardCharsets.UTF_8);
            return JSON.readTree(claims).path("email_verified").asBoolean(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    record AccountResponse(
            String localId,
            String email,
            @Nullable String displayName,
            String idToken,
            String refreshToken,
            String expiresIn) {}

    /** Identity Toolkit's answer to an account change. Tokens only come with changes that need new ones. */
    record UpdateResponse(
            @Nullable String idToken,
            @Nullable String refreshToken,
            @Nullable String expiresIn) {}

    /** Secure Token's answer, whose fields are in snake case, unlike Identity Toolkit's. */
    record RefreshResponse(
            @JsonProperty("user_id") String userId,
            @JsonProperty("id_token") String idToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") String expiresIn) {}
}

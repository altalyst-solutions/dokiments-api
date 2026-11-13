package com.dokiments.auth.adapter.out.google;

import com.dokiments.auth.application.port.out.GoogleOAuth;
import com.dokiments.platform.email.EmailProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Google's OAuth endpoints, called the way the web app calls them. */
@Component
class GoogleOAuthClient implements GoogleOAuth {

    private final GoogleOAuthProperties properties;
    private final String callbackUrl;
    private final RestClient tokens;

    GoogleOAuthClient(GoogleOAuthProperties properties, EmailProperties site, RestClient.Builder builder) {
        this.properties = properties;
        this.callbackUrl = GoogleOAuthProperties.callbackUrl(site.siteUrl());
        this.tokens = builder.clone().build();
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public URI authorizationUrl(String state) {
        return UriComponentsBuilder.fromUri(properties.authorizationUrl())
                .queryParam("client_id", Objects.requireNonNull(properties.clientId()))
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                // Lets people pick which Google account, instead of silently using the one signed in.
                .queryParam("prompt", "select_account")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    @Override
    public String exchangeCode(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", Objects.requireNonNull(properties.clientId()));
        form.add("client_secret", Objects.requireNonNull(properties.clientSecret()));
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", callbackUrl);
        TokenResponse response = tokens.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (_, refusal) -> {
                    throw new Refused("Google refused the code: "
                            + refusal.getStatusCode().value());
                })
                .body(TokenResponse.class);
        if (response == null || response.idToken() == null) {
            throw new Refused("Google answered without an ID token");
        }
        return response.idToken();
    }

    /** The part of Google's token answer that matters here. */
    record TokenResponse(@JsonProperty("id_token") @Nullable String idToken) {}
}

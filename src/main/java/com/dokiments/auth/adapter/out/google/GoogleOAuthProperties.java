package com.dokiments.auth.adapter.out.google;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google sign-in, under {@code dokiments.auth.google}. Without a client ID and secret, Google sign-in is off: the start
 * sends people back to the sign-in page, as the web app does, and everything else works.
 *
 * @param clientId the OAuth client's ID ({@code GOOGLE_CLIENT_ID})
 * @param clientSecret the OAuth client's secret ({@code GOOGLE_CLIENT_SECRET})
 * @param authorizationUrl Google's sign-in page. Only changed by tests.
 * @param tokenUrl Google's token endpoint. Only changed by tests, to point at a stand-in for Google.
 */
@ConfigurationProperties("dokiments.auth.google")
public record GoogleOAuthProperties(
        @Nullable String clientId,
        @Nullable String clientSecret,

        @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth")
        URI authorizationUrl,

        @DefaultValue("https://oauth2.googleapis.com/token") URI tokenUrl) {

    /** The path Google sends the browser back to. Registered with the OAuth client, so it never changes. */
    public static final String CALLBACK_PATH = "/api/auth/google/callback";

    public GoogleOAuthProperties {
        clientId = clientId == null || clientId.isBlank() ? null : clientId.strip();
        clientSecret = clientSecret == null || clientSecret.isBlank() ? null : clientSecret.strip();
    }

    public boolean configured() {
        return clientId != null && clientSecret != null;
    }

    /**
     * The callback on the site people use, for example {@code https://dokiments.com/api/auth/google/callback}: the web
     * app forwards it here, so Google's registration stays the same.
     */
    public static String callbackUrl(URI siteUrl) {
        return siteUrl.resolve(CALLBACK_PATH).toString();
    }

    @Override
    public String toString() {
        return "GoogleOAuthProperties[clientId=" + clientId + ", configured=" + configured() + "]";
    }
}

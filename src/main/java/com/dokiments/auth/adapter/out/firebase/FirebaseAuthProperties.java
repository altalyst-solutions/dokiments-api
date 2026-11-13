package com.dokiments.auth.adapter.out.firebase;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which Firebase Auth to use, under {@code dokiments.auth}.
 *
 * <p>Locally and in tests that is always the Auth emulator; deployed, the real Firebase Auth of the project. The same
 * safety rules as for Firestore apply, checked when the application starts (see {@link FirebaseAuthConfiguration}): the
 * emulator only runs with a {@code demo-} project, and where it is required, running without it is refused.
 *
 * @param apiKey the Firebase project's Web API key ({@code FIREBASE_API_KEY}). Required unless the emulator is used.
 * @param emulatorHost {@code host:port} of the Auth emulator ({@code FIREBASE_AUTH_EMULATOR_HOST})
 * @param requireEmulator whether only the emulator may be used: set in {@code dev} and tests
 */
@ConfigurationProperties("dokiments.auth")
public record FirebaseAuthProperties(
        @Nullable String apiKey,
        @Nullable String emulatorHost,
        @DefaultValue("false") boolean requireEmulator) {

    static final String IDENTITY_TOOLKIT = "https://identitytoolkit.googleapis.com";
    static final String SECURE_TOKEN = "https://securetoken.googleapis.com";

    public FirebaseAuthProperties {
        apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
        emulatorHost = emulatorHost == null || emulatorHost.isBlank() ? null : emulatorHost.strip();
        if (requireEmulator && emulatorHost == null) {
            throw new IllegalArgumentException("This profile only allows the Firebase Auth emulator, but no emulator is"
                    + " set. Set dokiments.auth.emulator-host (FIREBASE_AUTH_EMULATOR_HOST).");
        }
        if (emulatorHost == null && apiKey == null) {
            throw new IllegalArgumentException(
                    "dokiments.auth.api-key must be set (FIREBASE_API_KEY): the Firebase project's Web API key.");
        }
    }

    public boolean usesEmulator() {
        return emulatorHost != null;
    }

    // The emulator only speaks plain HTTP, on this machine or in the test container.

    /** Identity Toolkit (accounts), or the emulator's copy of it. */
    @SuppressWarnings("HttpUrlsUsage")
    String identityToolkitUrl() {
        return emulatorHost == null ? IDENTITY_TOOLKIT : "http://" + emulatorHost + "/identitytoolkit.googleapis.com";
    }

    /** Secure Token (refreshes), or the emulator's copy of it. */
    @SuppressWarnings("HttpUrlsUsage")
    String secureTokenUrl() {
        return emulatorHost == null ? SECURE_TOKEN : "http://" + emulatorHost + "/securetoken.googleapis.com";
    }

    /** The emulator accepts any key, but every request still carries one. */
    String keyForRequests() {
        return apiKey == null ? "emulator" : apiKey;
    }

    @Override
    public String toString() {
        return "FirebaseAuthProperties[emulatorHost=" + emulatorHost + ", requireEmulator=" + requireEmulator + "]";
    }
}

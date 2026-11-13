package com.dokiments.auth.adapter.out.firebase;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What to tell the user when Firebase Auth refuses a request, by the error code Firebase sends. The same wording as the
 * web app, which shows these messages on its forms.
 */
final class FirebaseErrors {

    static final String FALLBACK = "Something went wrong. Please try again.";

    /** In the web app's order. Firebase often adds a description after the code, so a code matches as a prefix. */
    private static final Map<String, String> MESSAGES = new LinkedHashMap<>();

    static {
        MESSAGES.put(
                "CREDENTIAL_TOO_OLD_LOGIN_AGAIN",
                "For your security, please sign out and sign in again before changing your password.");
        MESSAGES.put("EMAIL_EXISTS", "An account already exists for this email.");
        MESSAGES.put("EMAIL_NOT_FOUND", "No account was found for this email.");
        MESSAGES.put("INVALID_ID_TOKEN", "Your session is no longer valid. Please sign in again.");
        MESSAGES.put("INVALID_IDP_RESPONSE", "Google sign-in failed. Please try again.");
        MESSAGES.put("INVALID_LOGIN_CREDENTIALS", "The email or password is incorrect.");
        MESSAGES.put("INVALID_PASSWORD", "The email or password is incorrect.");
        MESSAGES.put("OPERATION_NOT_ALLOWED", "Email and password sign-in is not enabled in Firebase.");
        MESSAGES.put("TOKEN_EXPIRED", "Your session expired. Please sign in again.");
        MESSAGES.put("TOO_MANY_ATTEMPTS_TRY_LATER", "Too many attempts. Please try again later.");
        MESSAGES.put("USER_DISABLED", "This account has been disabled.");
        MESSAGES.put("WEAK_PASSWORD", "Use a stronger password with at least 6 characters.");
    }

    private FirebaseErrors() {}

    static String messageFor(@Nullable String firebaseMessage) {
        if (firebaseMessage == null) {
            return FALLBACK;
        }
        return MESSAGES.entrySet().stream()
                .filter(entry -> firebaseMessage.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FALLBACK);
    }
}

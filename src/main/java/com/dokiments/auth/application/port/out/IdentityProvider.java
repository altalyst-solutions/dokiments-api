package com.dokiments.auth.application.port.out;

import com.dokiments.auth.domain.AuthTokens;
import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Who checks passwords and issues tokens: Firebase Auth.
 *
 * <p>Every method throws {@link Refused} when Firebase refuses the request, and
 * {@link com.dokiments.shared.error.UpstreamUnavailableException} when it can't be reached. What a refusal means
 * depends on the use case: a wrong password is a {@code 401} when signing in, but a {@code 400} when confirming a
 * password for a change.
 */
public interface IdentityProvider {

    SignedIn signUp(String displayName, String email, String password);

    SignedIn signIn(String email, String password);

    /** New tokens for a refresh token. */
    Refreshed refresh(String refreshToken);

    /** Asks Firebase to email the account a link to confirm its address. */
    void sendVerificationEmail(String idToken);

    /** Asks Firebase to email a link to choose a new password, to whichever account has this address. */
    void sendPasswordResetEmail(String email);

    /** Changes the account's password. Firebase issues new tokens, because the old ones belong to the old password. */
    Updated changePassword(String idToken, String newPassword);

    /** Changes the name on the Firebase account. */
    Updated changeDisplayName(String idToken, String displayName);

    /**
     * An account that has just signed in or up.
     *
     * @param uid its Firebase Auth ID
     * @param email its address
     * @param displayName the name on the account, if it has one
     * @param emailVerified whether its address is confirmed
     * @param tokens its new tokens
     */
    record SignedIn(String uid, String email, @Nullable String displayName, boolean emailVerified, AuthTokens tokens) {}

    /**
     * The result of a refresh.
     *
     * @param uid the account's Firebase Auth ID
     * @param emailVerified whether its address is confirmed now
     * @param tokens the new tokens
     */
    record Refreshed(String uid, boolean emailVerified, AuthTokens tokens) {}

    /**
     * The result of a change to the account.
     *
     * @param tokens new tokens, when Firebase issued them; otherwise the current ones stay valid
     * @param emailVerified whether the address is confirmed, as of the new tokens; {@code null} without new tokens
     */
    record Updated(@Nullable AuthTokens tokens, @Nullable Boolean emailVerified) {}

    /** Firebase refused the request. */
    final class Refused extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Firebase's code when the session is valid but too old for this change. */
        public static final String CREDENTIAL_TOO_OLD = "CREDENTIAL_TOO_OLD_LOGIN_AGAIN";

        /** Firebase's code when it is throttling this account or address. */
        public static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS_TRY_LATER";

        private final String code;
        private final String userMessage;

        /**
         * @param code Firebase's code, such as {@code INVALID_LOGIN_CREDENTIALS}, for the logs and decisions
         * @param userMessage what to tell the user, in the web app's words
         */
        public Refused(String code, String userMessage) {
            super("Firebase Auth refused the request: " + code);
            this.code = code;
            this.userMessage = userMessage;
        }

        public boolean is(String firebaseCode) {
            return code.startsWith(firebaseCode);
        }

        public String userMessage() {
            return userMessage;
        }
    }
}

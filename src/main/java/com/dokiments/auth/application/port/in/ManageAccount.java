package com.dokiments.auth.application.port.in;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.shared.EmailAddresses;
import com.dokiments.shared.error.InvalidRequestException;
import com.dokiments.users.ProfileChanges;

/**
 * What a signed-in user can change about their account, and the password reset for those who can't sign in.
 *
 * <p>Each change that gets new tokens from Firebase returns the session with them, still ending when it would have.
 * Firebase's refusals are answered with {@code 400} and its message, unless noted otherwise.
 */
public interface ManageAccount {

    /**
     * Changes the password.
     *
     * @throws com.dokiments.auth.domain.ReauthenticationRequiredException if the session is too old for it ({@code 403}
     *     with {@code REAUTH_REQUIRED})
     * @param newPassword as sent; checked as a {@link NewPasswordCommand} once the account is known to have a password,
     *     so a Google account is told so whatever it sent
     * @throws InvalidRequestException for an account that signs in with Google, which has no password, or a refusal
     */
    AuthSession changePassword(AuthSession session, String newPassword);

    /**
     * Checks the password again, so a change that needs a recent sign-in can go ahead. The address always comes from
     * the session, so nobody can check someone else's password this way.
     *
     * @throws InvalidRequestException for a Google account, or a wrong password ("The email or password is incorrect.")
     */
    AuthSession reauthenticate(AuthSession session, String currentPassword);

    /** Changes the name and details in the settings, on the Firebase account and the profile. */
    AuthSession updateProfile(AuthSession session, UpdateProfileCommand command);

    /**
     * Asks Firebase to send another verification email, unless the address is verified already.
     *
     * @throws com.dokiments.shared.error.TooManyRequestsException when Firebase is throttling the account ({@code 429})
     */
    void resendVerificationEmail(AuthSession session);

    /** Asks Firebase to send a link to choose a new password. */
    void sendPasswordResetEmail(ResetPasswordCommand command);

    /** The web app's answer to a body that doesn't match what the endpoint expects. */
    String INVALID_BODY = "Invalid request body.";

    /**
     * A new password: present, at most 128 characters. No minimum: Firebase refuses a weak one with a message saying
     * what is needed.
     */
    record NewPasswordCommand(String password) {

        public NewPasswordCommand {
            if (password.isEmpty() || password.length() > 128) {
                throw new InvalidRequestException(INVALID_BODY);
            }
        }

        @Override
        public String toString() {
            return "NewPasswordCommand[]";
        }
    }

    /** The current password: present, and no maximum, so a long existing password can always be confirmed. */
    record CurrentPasswordCommand(String password) {

        public CurrentPasswordCommand {
            if (password.isEmpty()) {
                throw new InvalidRequestException(INVALID_BODY);
            }
        }

        @Override
        public String toString() {
            return "CurrentPasswordCommand[]";
        }
    }

    /** The address to send a reset link to, trimmed and plausible. */
    record ResetPasswordCommand(String email) {

        public ResetPasswordCommand {
            email = EmailAddresses.trim(email);
            if (EmailAddresses.isImplausible(email)) {
                throw new InvalidRequestException(INVALID_BODY);
            }
        }
    }

    /**
     * The settings, checked as the web app checks them: every field trimmed and within its limit, the name not empty,
     * and the business address either empty or a plausible address (not trimmed: it is printed on documents as typed).
     */
    record UpdateProfileCommand(ProfileChanges changes) {

        public static UpdateProfileCommand of(
                String displayName,
                String address,
                String brn,
                String businessEmail,
                String companyName,
                String fullName,
                String phone,
                String tel,
                String website) {
            var changes = new ProfileChanges(
                    within(displayName, 200),
                    within(address, 500),
                    within(brn, 50),
                    businessEmail,
                    within(companyName, 200),
                    within(fullName, 200),
                    within(phone, 50),
                    within(tel, 50),
                    within(website, 200));
            if (changes.displayName().isEmpty()
                    || (!businessEmail.isEmpty() && EmailAddresses.isImplausible(businessEmail))) {
                throw new InvalidRequestException(INVALID_BODY);
            }
            return new UpdateProfileCommand(changes);
        }

        private static String within(String value, int max) {
            String trimmed = value.strip();
            if (trimmed.length() > max) {
                throw new InvalidRequestException(INVALID_BODY);
            }
            return trimmed;
        }
    }
}

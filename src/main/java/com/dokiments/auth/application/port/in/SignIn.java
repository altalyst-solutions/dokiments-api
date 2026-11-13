package com.dokiments.auth.application.port.in;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.shared.EmailAddresses;
import com.dokiments.shared.error.InvalidRequestException;

/** Signs in with an email address and password. */
public interface SignIn {

    /**
     * Checks the credentials with Firebase and starts a session.
     *
     * @throws com.dokiments.shared.error.UnauthenticatedException if Firebase refuses them, with a message for the user
     *     such as "The email or password is incorrect."
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if Firebase couldn't be reached
     */
    AuthSession signIn(SignInCommand command);

    /**
     * The credentials, checked as the web app checks them: the address trimmed and plausible, the password present. The
     * password has no maximum: an account may hold a longer one than any limit set today, and refusing to pass it on
     * would lock its owner out.
     *
     * @param email the address as typed
     * @param password the password as typed
     */
    record SignInCommand(String email, String password) {

        public SignInCommand {
            email = EmailAddresses.trim(email);
            if (EmailAddresses.isImplausible(email) || password.isEmpty()) {
                throw new InvalidRequestException(Credentials.INVALID_BODY);
            }
        }

        @Override
        public String toString() {
            return "SignInCommand[email=" + email + "]";
        }
    }
}

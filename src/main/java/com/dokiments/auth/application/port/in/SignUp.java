package com.dokiments.auth.application.port.in;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.shared.EmailAddresses;
import com.dokiments.shared.error.InvalidRequestException;

/** Creates an account with an email address and password. */
public interface SignUp {

    /**
     * Creates the Firebase account and its profile, asks Firebase to send the address a verification email, and starts
     * a session. A verification email that can't be sent doesn't fail the sign-up: the user can ask for another.
     *
     * @throws com.dokiments.shared.error.UnauthenticatedException if Firebase refuses, with a message for the user such
     *     as "An account already exists for this email."
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if Firebase couldn't be reached
     */
    AuthSession signUp(SignUpCommand command);

    /**
     * The new account, checked as the web app checks it.
     *
     * <p>There is no minimum length for the password: Firebase refuses a weak one with a message that says what is
     * needed, which is more useful to the user than a generic invalid request.
     *
     * @param displayName trimmed, 1 to 200 characters: it is copied onto other records that limit its length
     * @param email the address as typed
     * @param password at most 128 characters
     */
    record SignUpCommand(String displayName, String email, String password) {

        static final int MAX_DISPLAY_NAME = 200;
        static final int MAX_PASSWORD = 128;

        public SignUpCommand {
            displayName = displayName.strip();
            email = EmailAddresses.trim(email);
            if (displayName.isEmpty()
                    || displayName.length() > MAX_DISPLAY_NAME
                    || EmailAddresses.isImplausible(email)
                    || password.isEmpty()
                    || password.length() > MAX_PASSWORD) {
                throw new InvalidRequestException(Credentials.INVALID_BODY);
            }
        }

        @Override
        public String toString() {
            return "SignUpCommand[email=" + email + "]";
        }
    }
}

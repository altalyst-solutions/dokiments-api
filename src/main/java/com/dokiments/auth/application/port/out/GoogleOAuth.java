package com.dokiments.auth.application.port.out;

import java.io.Serial;
import java.net.URI;

/** Google's OAuth sign-in: where to send the browser, and turning Google's answer into proof of who signed in. */
public interface GoogleOAuth {

    /** Whether Google sign-in is set up here (a client ID and secret). */
    boolean configured();

    /** Google's sign-in page, which sends the browser back to the callback with a code and {@code state}. */
    URI authorizationUrl(String state);

    /**
     * Exchanges the one-time code from the callback for a Google ID token, which Firebase accepts as proof.
     *
     * @throws Refused if Google refuses the code (expired, used already, or for another client)
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if Google couldn't be reached
     */
    String exchangeCode(String code);

    /** Google refused the code. */
    final class Refused extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public Refused(String reason) {
            super(reason);
        }
    }
}

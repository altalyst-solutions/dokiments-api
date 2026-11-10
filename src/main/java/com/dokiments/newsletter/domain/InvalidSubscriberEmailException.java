package com.dokiments.newsletter.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * The address to subscribe isn't a plausible email address. Answered with {@code 400} and the web app's generic message
 * for a body that doesn't match what the endpoint expects, which is what it answers today. The address itself is never
 * repeated in the response or the logs.
 */
public class InvalidSubscriberEmailException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording, kept in step with {@code platform.web.ErrorMessages.INVALID_BODY} by a test. */
    static final String MESSAGE = "Invalid request body.";

    public InvalidSubscriberEmailException() {
        super(MESSAGE);
    }
}

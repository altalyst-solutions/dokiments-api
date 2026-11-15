package com.dokiments.feedback.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * The feedback breaks one of the rules, such as an empty message. Answered with {@code 400} and the web app's generic
 * message for a body that doesn't match what the endpoint expects.
 */
public class InvalidFeedbackException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording, the same as {@code platform.web.ErrorMessages.INVALID_BODY}. */
    static final String MESSAGE = "Invalid request body.";

    public InvalidFeedbackException() {
        super(MESSAGE);
    }
}

package com.dokiments.clients.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * A client's details break one of the rules, such as a missing name or an implausible address. Answered with
 * {@code 400} and the web app's generic message for a body that doesn't match what the endpoint expects, which is what
 * it answers today. What exactly was wrong isn't repeated in the response.
 */
public class InvalidClientException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording, the same as {@code platform.web.ErrorMessages.INVALID_BODY}. */
    static final String MESSAGE = "Invalid request body.";

    public InvalidClientException() {
        super(MESSAGE);
    }
}

package com.dokiments.sharing.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * What was sent to share or sign breaks a rule, such as an implausible address. Answered with {@code 400} and the web
 * app's generic message for an invalid body.
 */
public class InvalidShareException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Invalid request body.";

    public InvalidShareException() {
        super(MESSAGE);
    }
}

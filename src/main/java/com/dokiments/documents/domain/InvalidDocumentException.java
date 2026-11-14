package com.dokiments.documents.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * A document's name or values break one of the rules. Answered with {@code 400} and the web app's generic message for a
 * body that doesn't match what the endpoint expects.
 */
public class InvalidDocumentException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording, the same as {@code platform.web.ErrorMessages.INVALID_BODY}. */
    static final String MESSAGE = "Invalid request body.";

    public InvalidDocumentException() {
        super(MESSAGE);
    }
}

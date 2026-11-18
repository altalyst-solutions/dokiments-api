package com.dokiments.sharing.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/** The address is the owner's own. Answered with {@code 400}. */
public class SelfShareException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "You cannot share a document with yourself.";

    public SelfShareException() {
        super(MESSAGE);
    }
}

package com.dokiments.sharing.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/** The document's template has no field for the recipient's signature. Answered with {@code 400}. */
public class NotSignableException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE =
            "This type of document has no recipient signature, so it cannot be shared for signing.";

    public NotSignableException() {
        super(MESSAGE);
    }
}

package com.dokiments.sharing.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/**
 * The copy was shared to be viewed, since its template has no field for the recipient's signature. Answered with
 * {@code 400}.
 */
public class ViewOnlyException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE =
            "This document has no recipient signature. It was shared to view, so it cannot be signed.";

    public ViewOnlyException() {
        super(MESSAGE);
    }
}

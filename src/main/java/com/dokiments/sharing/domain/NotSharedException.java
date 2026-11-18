package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ConflictException;
import java.io.Serial;

/** There is no recipient with access to take the copy back from. Answered with {@code 409}. */
public class NotSharedException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This document is not shared with anyone.";

    public NotSharedException() {
        super(MESSAGE);
    }
}

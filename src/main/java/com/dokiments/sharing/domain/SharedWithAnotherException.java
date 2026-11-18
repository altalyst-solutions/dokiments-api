package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ConflictException;
import java.io.Serial;

/** The copy is shared with someone else; it has one recipient at a time. Answered with {@code 409}. */
public class SharedWithAnotherException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE =
            "This document is already shared with someone else. Sharing with more than one person is not available yet.";

    public SharedWithAnotherException() {
        super(MESSAGE);
    }
}

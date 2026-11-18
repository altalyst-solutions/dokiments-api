package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ConflictException;
import java.io.Serial;

/** The shared copy has been signed, and a signed copy never changes. Answered with {@code 409}. */
public class DocumentLockedException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This document has been signed and can no longer be changed.";

    public DocumentLockedException() {
        super(MESSAGE);
    }
}

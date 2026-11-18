package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ConflictException;
import java.io.Serial;

/** Another request changed the copy at the same moment, and Firestore gave up retrying. Answered with {@code 409}. */
public class ShareRaceException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This document was just shared by another request. Please try again.";

    public ShareRaceException() {
        super(MESSAGE);
    }
}

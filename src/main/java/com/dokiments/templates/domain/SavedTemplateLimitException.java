package com.dokiments.templates.domain;

import com.dokiments.shared.error.ForbiddenException;
import java.io.Serial;

/** A free account already keeps as many saved templates as it may. */
public class SavedTemplateLimitException extends ForbiddenException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "You've reached your saved template limit.";

    public SavedTemplateLimitException() {
        super(MESSAGE);
    }
}

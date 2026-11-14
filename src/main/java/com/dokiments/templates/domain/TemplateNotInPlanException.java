package com.dokiments.templates.domain;

import com.dokiments.shared.error.ForbiddenException;
import java.io.Serial;

/** The user's plan doesn't include the template's tier. */
public class TemplateNotInPlanException extends ForbiddenException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Your plan does not include this template.";

    public TemplateNotInPlanException() {
        super(MESSAGE);
    }
}

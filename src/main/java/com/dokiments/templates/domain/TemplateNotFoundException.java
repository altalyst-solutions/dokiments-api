package com.dokiments.templates.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/** No template with this ID in the catalog, or one that is hidden there. */
public class TemplateNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Template not found.";

    public TemplateNotFoundException() {
        super(MESSAGE);
    }
}

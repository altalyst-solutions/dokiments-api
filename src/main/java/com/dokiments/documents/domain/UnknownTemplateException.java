package com.dokiments.documents.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/** The template to make a document from isn't in the catalog, or is hidden there. */
public class UnknownTemplateException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Template not found.";

    public UnknownTemplateException() {
        super(MESSAGE);
    }
}

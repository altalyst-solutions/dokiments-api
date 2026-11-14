package com.dokiments.documents.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/**
 * No document with this ID among the user's own. Another user's document is answered the same way, since only the
 * user's own documents are ever looked in.
 */
public class DocumentNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Document not found.";

    public DocumentNotFoundException() {
        super(MESSAGE);
    }
}

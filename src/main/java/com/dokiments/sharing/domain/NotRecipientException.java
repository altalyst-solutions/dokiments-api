package com.dokiments.sharing.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/**
 * The copy doesn't exist, or the user may not see it. Both are answered the same way, so IDs can't be probed. Answered
 * with {@code 404}.
 */
public class NotRecipientException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "You do not have access to this shared document.";

    public NotRecipientException() {
        super(MESSAGE);
    }
}

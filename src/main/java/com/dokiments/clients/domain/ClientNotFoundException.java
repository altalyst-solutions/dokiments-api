package com.dokiments.clients.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/**
 * No client with this ID in the user's own list. A client of another user is answered the same way, since only the
 * user's own list is ever looked in.
 */
public class ClientNotFoundException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Client not found.";

    public ClientNotFoundException() {
        super(MESSAGE);
    }
}

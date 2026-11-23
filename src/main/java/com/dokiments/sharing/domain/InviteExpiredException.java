package com.dokiments.sharing.domain;

import com.dokiments.shared.error.GoneException;
import java.io.Serial;

/**
 * The invitation link is more than 14 days old. Answered with {@code 410}, so the web app can say it expired rather
 * than that it never existed.
 */
public class InviteExpiredException extends GoneException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This invitation has expired.";

    public InviteExpiredException() {
        super(MESSAGE);
    }
}

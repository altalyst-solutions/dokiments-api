package com.dokiments.sharing.domain;

import com.dokiments.shared.error.NotFoundException;
import java.io.Serial;

/**
 * The invitation link doesn't work: unknown, or for a copy that was taken back, signed or shared afresh. Answered with
 * {@code 404}.
 */
public class InviteInvalidException extends NotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This invitation is no longer valid.";

    public InviteInvalidException() {
        super(MESSAGE);
    }
}

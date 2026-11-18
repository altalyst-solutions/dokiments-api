package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ForbiddenException;
import java.io.Serial;

/** The owner's plan allows no more people to share with. Answered with {@code 403}. */
public class ShareLimitReachedException extends ForbiddenException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "You have reached the sharing limit for your plan.";

    public ShareLimitReachedException() {
        super(MESSAGE);
    }
}

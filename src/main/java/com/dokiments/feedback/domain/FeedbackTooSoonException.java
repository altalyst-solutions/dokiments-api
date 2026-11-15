package com.dokiments.feedback.domain;

import com.dokiments.shared.error.TooManyRequestsException;
import java.io.Serial;

/** The account sent feedback less than a minute ago. Answered with {@code 429}. */
public class FeedbackTooSoonException extends TooManyRequestsException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording, which it shows as it is, dash included. */
    static final String MESSAGE =
            "Thanks — we just received your last message. Please wait a moment before sending another.";

    public FeedbackTooSoonException() {
        super(MESSAGE, null);
    }
}

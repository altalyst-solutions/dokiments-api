package com.dokiments.promo.domain;

import com.dokiments.shared.error.InvalidRequestException;
import java.io.Serial;

/** The code matches no promotion. Answered with {@code 400}. */
public class InvalidPromoCodeException extends InvalidRequestException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "Invalid promo code.";

    public InvalidPromoCodeException() {
        super(MESSAGE);
    }
}

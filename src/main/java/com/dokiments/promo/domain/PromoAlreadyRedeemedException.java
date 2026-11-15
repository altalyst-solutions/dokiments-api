package com.dokiments.promo.domain;

import com.dokiments.shared.error.ConflictException;
import java.io.Serial;

/** The account redeemed this promotion already. Answered with {@code 409}. */
public class PromoAlreadyRedeemedException extends ConflictException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This promo code has already been used for your account.";

    public PromoAlreadyRedeemedException() {
        super(MESSAGE);
    }
}

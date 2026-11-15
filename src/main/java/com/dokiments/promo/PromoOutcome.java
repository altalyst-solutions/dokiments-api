package com.dokiments.promo;

import java.util.Locale;

/** What happened to a promo code given while signing up or in. */
public enum PromoOutcome {
    /** The code was redeemed for the account. */
    APPLIED,
    /** The account had redeemed this code already. */
    ALREADY_REDEEMED,
    /** The code matches no promotion. */
    INVALID,
    /** The code is right, but the redemption couldn't be stored; it can be tried again from the settings. */
    FAILED;

    /** The API form the web app shows a message for, for example {@code already_redeemed}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

package com.dokiments.sharing.domain;

import java.util.Locale;

/** What an invitation link leads to. */
public enum InviteStatus {
    /** It works. */
    VALID,
    /** It worked, but more than 14 days have passed. */
    EXPIRED,
    /** It never worked, or no longer does: an unknown link, or a copy taken back or shared afresh. */
    INVALID;

    /** The API form, for example {@code expired}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}

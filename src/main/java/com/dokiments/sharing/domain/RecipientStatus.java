package com.dokiments.sharing.domain;

import java.util.Locale;

/** How far a recipient has got with a shared copy. */
public enum RecipientStatus {
    /** Shared with them, not opened yet. */
    PENDING,
    /** They opened it. */
    VIEWED,
    /** They signed it. */
    SIGNED,
    /** The owner took it back. */
    REVOKED;

    /** The stored and API form, for example {@code pending}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The status a stored value names, or {@link #PENDING} for anything else, as the web app reads it. */
    public static RecipientStatus fromStored(String value) {
        for (RecipientStatus status : values()) {
            if (status.value().equals(value)) {
                return status;
            }
        }
        return PENDING;
    }
}

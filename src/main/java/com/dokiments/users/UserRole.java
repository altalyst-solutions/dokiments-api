package com.dokiments.users;

import java.util.Locale;

/** The account's plan or privileges. New accounts are always {@link #FREE}; upgrades happen outside the API. */
public enum UserRole {
    SUPERADMIN,
    FREE,
    SILVER,
    GOLD,
    SPECIAL;

    /** The stored and API form, for example {@code free}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The role a stored value names, or {@link #FREE} for anything else, as in the web app. */
    public static UserRole fromValue(String value) {
        for (UserRole role : values()) {
            if (role.value().equals(value)) {
                return role;
            }
        }
        return FREE;
    }
}

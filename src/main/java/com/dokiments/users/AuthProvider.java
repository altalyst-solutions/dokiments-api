package com.dokiments.users;

import java.util.Locale;

/** How an account was first created. Set once and never changed; decides whether password settings apply. */
public enum AuthProvider {
    PASSWORD,
    GOOGLE;

    /** The stored and API form, for example {@code password}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The provider a stored value names, or {@link #PASSWORD} for anything else: profiles created before Google sign-in
     * existed have no value, and they are all password accounts.
     */
    public static AuthProvider fromValue(String value) {
        return GOOGLE.value().equals(value) ? GOOGLE : PASSWORD;
    }
}

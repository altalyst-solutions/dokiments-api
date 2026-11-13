package com.dokiments.shared;

import java.util.regex.Pattern;

/**
 * The web app's rules for an email address as someone types it, shared by every module that accepts one, so all of them
 * accept exactly the same addresses.
 */
public final class EmailAddresses {

    /** The longest address email standards allow. */
    public static final int MAX_LENGTH = 254;

    /**
     * The web app's check (zod's "practical" email rule), so both accept the same addresses. Stricter than Bean
     * Validation's {@code @Email}, which accepts addresses such as {@code a@b} without a domain ending. Only ASCII
     * letters are allowed, so changing the case can't change whether an address matches.
     */
    private static final Pattern PLAUSIBLE = Pattern.compile(
            "^(?:[A-Za-z0-9_'+\\-]+\\.)*[A-Za-z0-9_'+\\-]*[A-Za-z0-9_+-]@(?:[A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$");

    /**
     * Spaces around an address, as JavaScript's {@code trim()} removes them. Java's {@code strip()} keeps non-breaking
     * spaces and the byte order mark, which a pasted address can carry, so they are listed too.
     */
    private static final Pattern SURROUNDING_SPACE =
            Pattern.compile("^[\\s\\u00A0\\u2007\\u202F\\uFEFF]+|[\\s\\u00A0\\u2007\\u202F\\uFEFF]+$");

    private EmailAddresses() {}

    /** The address without the surrounding spaces. */
    public static String trim(String typed) {
        return SURROUNDING_SPACE.matcher(typed).replaceAll("");
    }

    /** Whether an already trimmed address is implausible, or longer than {@value #MAX_LENGTH} characters. */
    public static boolean isImplausible(String trimmed) {
        return trimmed.length() > MAX_LENGTH || !PLAUSIBLE.matcher(trimmed).matches();
    }
}

package com.dokiments.newsletter.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The address someone subscribed with, cleaned up the way the web app does it: surrounding spaces removed and lower
 * case, so subscribing again with {@code " Jane@Example.com "} is the same subscription as {@code jane@example.com}.
 *
 * @param value the cleaned address
 */
public record SubscriberEmail(String value) {

    /** The longest address email standards allow. */
    static final int MAX_LENGTH = 254;

    /**
     * The same rule the web app applies (zod's "practical" email check), so both accept exactly the same addresses.
     * Stricter than Bean Validation's {@code @Email}, which accepts addresses such as {@code a@b} without a domain
     * ending. Only ASCII letters are allowed, so lower-casing can't change whether an address matches.
     */
    private static final Pattern PLAUSIBLE = Pattern.compile(
            "^(?:[A-Za-z0-9_'+\\-]+\\.)*[A-Za-z0-9_'+\\-]*[A-Za-z0-9_+-]@(?:[A-Za-z0-9][A-Za-z0-9\\-]*\\.)+[A-Za-z]{2,}$");

    /**
     * Spaces around the address, as JavaScript's {@code trim()} removes them. Java's {@code strip()} keeps the
     * non-breaking spaces and the byte order mark, which a pasted address can carry, so they are listed too.
     */
    private static final Pattern SURROUNDING_SPACE =
            Pattern.compile("^[\\s\\u00A0\\u2007\\u202F\\uFEFF]+|[\\s\\u00A0\\u2007\\u202F\\uFEFF]+$");

    public SubscriberEmail {
        if (value.length() > MAX_LENGTH || !PLAUSIBLE.matcher(value).matches()) {
            throw new InvalidSubscriberEmailException();
        }
    }

    /**
     * Cleans and checks an address as typed.
     *
     * @throws InvalidSubscriberEmailException if it isn't a plausible address, or is longer than 254 characters
     */
    public static SubscriberEmail of(String typed) {
        String trimmed = SURROUNDING_SPACE.matcher(typed).replaceAll("");
        return new SubscriberEmail(trimmed.toLowerCase(Locale.ROOT));
    }
}

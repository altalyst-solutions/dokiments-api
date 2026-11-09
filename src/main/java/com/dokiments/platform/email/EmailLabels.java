package com.dokiments.platform.email;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Makes values that users typed, such as a document title, safe for a subject line or one line of an email.
 *
 * <p>The templates already escape markup. This guards against what escaping doesn't cover: line breaks that could break
 * a subject line or fake extra lines in the body, and names long enough to swamp the email.
 */
public final class EmailLabels {

    /** Control characters, including line breaks and the Unicode line and paragraph separators. */
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x7f-\\x9f\\u2028\\u2029]+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** For example {@code 31 Oct 2026, 21:30 UTC}: always UTC, so the time is unambiguous wherever it is read. */
    private static final DateTimeFormatter UTC_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.UK).withZone(ZoneOffset.UTC);

    private EmailLabels() {}

    /**
     * Replaces control characters and line breaks with spaces, collapses runs of whitespace and cuts anything longer
     * than {@code max} characters with an ellipsis.
     *
     * @param fallback returned when nothing is left after cleaning
     */
    public static String clean(String value, int max, String fallback) {
        String cleaned = WHITESPACE
                .matcher(CONTROL.matcher(value).replaceAll(" "))
                .replaceAll(" ")
                .strip();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (cleaned.codePointCount(0, cleaned.length()) <= max) {
            return cleaned;
        }
        // Cut on a code point boundary, so an emoji or other character outside the basic range is never split.
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, max - 1)).stripTrailing() + "…";
    }

    /** A person's name for an email: their display name, cleaned and at most 60 characters, or their address. */
    public static String person(String displayName, String email) {
        return clean(displayName, 60, email);
    }

    /** A time for an email, for example {@code 31 Oct 2026, 21:30 UTC}. */
    public static String utcTime(Instant time) {
        return UTC_TIME.format(time);
    }
}

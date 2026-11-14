package com.dokiments.shared;

import java.util.regex.Pattern;

/** Text as someone typed it into a form, cleaned the way the web app cleans it before checking it. */
public final class TypedText {

    /**
     * Spaces around the text, as JavaScript's {@code trim()} removes them. Java's {@code strip()} keeps non-breaking
     * spaces and the byte order mark, which pasted text can carry, so they are listed too.
     */
    private static final Pattern SURROUNDING_SPACE =
            Pattern.compile("^[\\s\\u00A0\\u2007\\u202F\\uFEFF]+|[\\s\\u00A0\\u2007\\u202F\\uFEFF]+$");

    private TypedText() {}

    /** The text without the surrounding spaces. */
    public static String trim(String typed) {
        return SURROUNDING_SPACE.matcher(typed).replaceAll("");
    }
}

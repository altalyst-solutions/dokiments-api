package com.dokiments.documents.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The web app's rules for what a user writes into a document: its name and the values of its form. */
public final class DocumentContent {

    /** The longest name, in characters. */
    static final int MAX_NAME = 200;

    /** The most values a document may have. The largest template has fewer than twenty fields. */
    static final int MAX_VALUES = 50;

    /** The longest value, in characters. Generous for a long text field. */
    static final int MAX_VALUE = 20_000;

    private DocumentContent() {}

    /**
     * A name as typed: not empty and at most 200 characters. It isn't trimmed, as in the web app.
     *
     * @throws InvalidDocumentException if it breaks the rule
     */
    public static String name(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new InvalidDocumentException();
        }
        return name;
    }

    /**
     * Values as sent: at most 50, each text of at most 20,000 characters. The limits stop an oversized request rather
     * than constrain real use.
     *
     * @throws InvalidDocumentException if they break a rule, or a value is missing
     */
    public static Map<String, String> values(Map<String, ? extends @Nullable String> values) {
        if (values.size() > MAX_VALUES) {
            throw new InvalidDocumentException();
        }
        var checked = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> {
            if (value == null || value.length() > MAX_VALUE) {
                throw new InvalidDocumentException();
            }
            checked.put(key, value);
        });
        return Collections.unmodifiableMap(checked);
    }
}

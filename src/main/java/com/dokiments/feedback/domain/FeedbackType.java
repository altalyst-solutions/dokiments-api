package com.dokiments.feedback.domain;

import java.util.Locale;
import java.util.Optional;

/** What kind of feedback it is: general feedback, or a report of a problem. */
public enum FeedbackType {
    FEEDBACK,
    PROBLEM;

    /** The stored and API form, for example {@code problem}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The type exactly as named, or empty for anything else. */
    public static Optional<FeedbackType> parse(String value) {
        for (FeedbackType type : values()) {
            if (type.value().equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

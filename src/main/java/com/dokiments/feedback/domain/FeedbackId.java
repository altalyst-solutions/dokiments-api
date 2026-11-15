package com.dokiments.feedback.domain;

import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * The ID of a piece of feedback, which is also its document ID in Firestore, in the web app's form,
 * {@code feedback_<time>_<random>}.
 *
 * @param value the ID
 */
public record FeedbackId(String value) {

    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

    /** A new ID: the time in base 36 and six random base-36 characters, as the web app makes them. */
    public static FeedbackId generate(Instant now, RandomGenerator random) {
        var id = new StringBuilder("feedback_")
                .append(Long.toString(now.toEpochMilli(), 36))
                .append('_');
        for (int i = 0; i < 6; i++) {
            id.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return new FeedbackId(id.toString());
    }
}

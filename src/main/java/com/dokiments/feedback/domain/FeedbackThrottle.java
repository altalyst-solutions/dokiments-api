package com.dokiments.feedback.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * How often an account may send feedback: once a minute. That is far below anyone's pace of writing a considered
 * message; it stops a single account from filling a collection nobody watches all the time.
 */
public final class FeedbackThrottle {

    /** How long an account waits between two pieces of feedback. */
    public static final Duration WINDOW = Duration.ofMinutes(1);

    private FeedbackThrottle() {}

    /** Whether feedback may be sent now, given when the account last sent some ({@code null} if never). */
    public static boolean allows(@Nullable Instant lastSentAt, Instant now) {
        return lastSentAt == null || !now.isBefore(lastSentAt.plus(WINDOW));
    }
}

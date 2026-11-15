package com.dokiments.feedback.domain;

import java.time.Instant;

/**
 * Feedback as kept, for an administrator to read: what was sent, and who sent it when.
 *
 * <p>Who sent it comes from the session, never from the request, so feedback can't be sent in someone else's name. The
 * address and name are copied as they were then, and cut to the length the web app keeps: they come from the account,
 * not from anything typed with the message, so a long name must never make the feedback fail.
 *
 * @param id the feedback's ID
 * @param uid the sender's Firebase Auth ID
 * @param email the sender's address, at most 254 characters
 * @param displayName the sender's name, at most 200 characters
 * @param message what was sent
 * @param createdAt when it was sent
 */
public record Feedback(
        FeedbackId id, String uid, String email, String displayName, FeedbackMessage message, Instant createdAt) {

    static final int MAX_EMAIL = 254;
    static final int MAX_DISPLAY_NAME = 200;

    /** Feedback just sent, with the sender's address and name cut to their limits. */
    public static Feedback sent(
            FeedbackId id, String uid, String email, String displayName, FeedbackMessage message, Instant now) {
        return new Feedback(id, uid, clamp(email, MAX_EMAIL), clamp(displayName, MAX_DISPLAY_NAME), message, now);
    }

    /**
     * The first {@code max} characters, counted as the web app counts them: by character rather than by Java's two-part
     * encoding of some of them, so a character such as an emoji is never cut in half.
     */
    static String clamp(String value, int max) {
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max));
    }
}

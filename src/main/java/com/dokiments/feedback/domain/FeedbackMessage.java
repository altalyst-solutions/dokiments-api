package com.dokiments.feedback.domain;

import com.dokiments.shared.TypedText;
import org.jspecify.annotations.Nullable;

/**
 * What the user sent: the message, the kind of feedback, and the page they sent it from.
 *
 * @param message what the user wrote, trimmed
 * @param path the page the user was on, such as {@code /my-documents}, trimmed; empty when the web app sent none
 * @param type what kind of feedback it is
 */
public record FeedbackMessage(String message, String path, FeedbackType type) {

    static final int MAX_MESSAGE = 2000;
    static final int MAX_PATH = 512;

    /**
     * Feedback as sent, checked as the web app checks it: the message trimmed and 1 to 2000 characters, the page
     * trimmed and at most 512, and the type one the web app offers.
     *
     * @throws InvalidFeedbackException if a rule is broken
     */
    public static FeedbackMessage of(String message, @Nullable String path, String type) {
        String trimmedMessage = TypedText.trim(message);
        String trimmedPath = path == null ? "" : TypedText.trim(path);
        if (trimmedMessage.isEmpty() || trimmedMessage.length() > MAX_MESSAGE || trimmedPath.length() > MAX_PATH) {
            throw new InvalidFeedbackException();
        }
        return new FeedbackMessage(
                trimmedMessage, trimmedPath, FeedbackType.parse(type).orElseThrow(InvalidFeedbackException::new));
    }
}

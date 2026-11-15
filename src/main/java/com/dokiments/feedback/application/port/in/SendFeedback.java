package com.dokiments.feedback.application.port.in;

import com.dokiments.feedback.domain.Feedback;
import com.dokiments.feedback.domain.FeedbackMessage;
import com.dokiments.shared.SignedInUser;

/** Sends feedback from the signed-in user, for an administrator to read. */
public interface SendFeedback {

    /**
     * Keeps the feedback, from {@code sender}.
     *
     * @throws com.dokiments.feedback.domain.FeedbackTooSoonException if the account sent feedback less than a minute
     *     ago
     */
    Feedback send(SignedInUser sender, FeedbackMessage message);
}

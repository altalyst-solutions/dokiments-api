package com.dokiments.feedback.application.port.out;

import com.dokiments.feedback.domain.Feedback;

/** Where feedback is kept, with each account's time of sending. */
public interface FeedbackInbox {

    /**
     * Keeps the feedback and records when the account sent it. Both happen as one step with checking the throttle. So
     * two pieces sent at the same moment can't both get past it, and feedback is never kept without that record.
     *
     * @return {@code false} if the account sent feedback too recently, in which case nothing is kept
     */
    boolean keep(Feedback feedback);
}

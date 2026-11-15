package com.dokiments.feedback.application.service;

import com.dokiments.feedback.application.port.in.SendFeedback;
import com.dokiments.feedback.application.port.out.FeedbackInbox;
import com.dokiments.feedback.domain.Feedback;
import com.dokiments.feedback.domain.FeedbackId;
import com.dokiments.feedback.domain.FeedbackMessage;
import com.dokiments.feedback.domain.FeedbackTooSoonException;
import com.dokiments.shared.SignedInUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

@Service
class FeedbackService implements SendFeedback {

    private final FeedbackInbox inbox;
    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    FeedbackService(FeedbackInbox inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Override
    public Feedback send(SignedInUser sender, FeedbackMessage message) {
        // Milliseconds, the precision the web app stores and answers with.
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var feedback = Feedback.sent(
                FeedbackId.generate(now, random), sender.uid(), sender.email(), sender.displayName(), message, now);
        if (!inbox.keep(feedback)) {
            throw new FeedbackTooSoonException();
        }
        return feedback;
    }
}

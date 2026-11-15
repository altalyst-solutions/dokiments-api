package com.dokiments.feedback.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.feedback.domain.Feedback;
import com.dokiments.feedback.domain.FeedbackMessage;
import com.dokiments.feedback.domain.FeedbackTooSoonException;
import com.dokiments.feedback.domain.FeedbackType;
import com.dokiments.shared.SignedInUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class FeedbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T09:30:00.123456Z");
    private static final FeedbackMessage MESSAGE = new FeedbackMessage("It broke", "/x", FeedbackType.PROBLEM);

    private final List<Feedback> kept = new ArrayList<>();
    private boolean tooSoon;
    private final FeedbackService service = new FeedbackService(
            feedback -> {
                if (tooSoon) {
                    return false;
                }
                kept.add(feedback);
                return true;
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void feedbackIsFromTheSignedInUserWithTheTimeInMilliseconds() {
        var feedback = service.send(new Sender("Jane"), MESSAGE);

        assertThat(feedback.uid()).isEqualTo("uid-1");
        assertThat(feedback.email()).isEqualTo("jane@example.com");
        assertThat(feedback.displayName()).isEqualTo("Jane");
        assertThat(feedback.createdAt()).isEqualTo(Instant.parse("2026-11-14T09:30:00.123Z"));
        assertThat(feedback.id().value()).startsWith("feedback_");
        assertThat(kept).containsExactly(feedback);
    }

    @Test
    void aLongNameIsCutRatherThanRefused() {
        var feedback = service.send(new Sender("J".repeat(300)), MESSAGE);

        assertThat(feedback.displayName()).hasSize(200);
    }

    @Test
    void feedbackTooSoonAfterTheLastIsRefused() {
        tooSoon = true;

        assertThatThrownBy(() -> service.send(new Sender("Jane"), MESSAGE))
                .isInstanceOf(FeedbackTooSoonException.class)
                .hasMessage(
                        "Thanks — we just received your last message. Please wait a moment before sending another.");
        assertThat(kept).isEmpty();
    }

    /** Jane, signed in, with the name the test needs. */
    private record Sender(String displayName) implements SignedInUser {

        @Override
        public String uid() {
            return "uid-1";
        }

        @Override
        public String email() {
            return "jane@example.com";
        }
    }
}

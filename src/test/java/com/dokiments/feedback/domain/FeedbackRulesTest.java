package com.dokiments.feedback.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Random;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class FeedbackRulesTest {

    @Test
    void theMessageAndPathAreTrimmedAndThePathMayBeLeftOut() {
        assertThat(FeedbackMessage.of("\u00A0 It broke \n", " /my-documents ", "problem"))
                .isEqualTo(new FeedbackMessage("It broke", "/my-documents", FeedbackType.PROBLEM));
        assertThat(FeedbackMessage.of("Nice", null, "feedback").path()).isEmpty();
    }

    @Test
    void theMessageIsOneToTwoThousandCharactersAndThePathAtMostFiveHundredTwelve() {
        assertThat(FeedbackMessage.of(" " + "m".repeat(2000) + " ", "p".repeat(512), "feedback")
                        .message())
                .hasSize(2000);

        assertInvalid(() -> FeedbackMessage.of("   ", null, "feedback"));
        assertInvalid(() -> FeedbackMessage.of("m".repeat(2001), null, "feedback"));
        assertInvalid(() -> FeedbackMessage.of("Hi", "p".repeat(513), "feedback"));
    }

    @Test
    void theTypeIsOneTheWebAppOffers() {
        assertInvalid(() -> FeedbackMessage.of("Hi", null, "bug"));
        assertInvalid(() -> FeedbackMessage.of("Hi", null, "Problem"));
    }

    @Test
    void theSendersAddressAndNameAreCutByCharacterNeverInsideOne() {
        String emoji = "😀";
        String name = "a".repeat(199) + emoji + "tail";

        String clamped = Feedback.clamp(name, 200);

        assertThat(clamped).isEqualTo("a".repeat(199) + emoji);
        assertThat(Feedback.clamp("Jane", 200)).isEqualTo("Jane");
    }

    @Test
    void feedbackMayBeSentOnceAMinute() {
        Instant last = Instant.parse("2026-11-14T09:00:00Z");

        assertThat(FeedbackThrottle.allows(null, last)).isTrue();
        assertThat(FeedbackThrottle.allows(last, last.plusSeconds(59))).isFalse();
        assertThat(FeedbackThrottle.allows(last, last.plusSeconds(60))).isTrue();
    }

    @Test
    void aNewIdHasTheWebAppsForm() {
        assertThat(FeedbackId.generate(Instant.ofEpochMilli(1_763_000_000_000L), new Random(1))
                        .value())
                .matches("feedback_" + Long.toString(1_763_000_000_000L, 36) + "_[0-9a-z]{6}");
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidFeedbackException.class).hasMessage("Invalid request body.");
    }
}

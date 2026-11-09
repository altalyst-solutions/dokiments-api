package com.dokiments.platform.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Settings that would make the time limits meaningless are refused at start-up. */
class HttpClientPropertiesTest {

    @Test
    void theDefaultsAreValid() {
        var properties = properties(Duration.ofSeconds(5), Duration.ofSeconds(8), 3);

        assertThat(properties.maxAttempts()).isEqualTo(3);
    }

    @Test
    void anAttemptLongerThanTheWholeCallIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Duration.ofSeconds(10), Duration.ofSeconds(8), 3))
                .withMessageContaining("total-timeout must be at least as long as each attempt timeout");
    }

    @Test
    void aZeroTimeoutIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Duration.ZERO, Duration.ofSeconds(8), 3))
                .withMessageContaining("attempt-timeout must be longer than zero");
    }

    @Test
    void noAttemptsAtAllIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(Duration.ofSeconds(5), Duration.ofSeconds(8), 0))
                .withMessageContaining("max-attempts must be at least 1");
    }

    private static HttpClientProperties properties(Duration attemptTimeout, Duration totalTimeout, int maxAttempts) {
        return new HttpClientProperties(
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                attemptTimeout,
                totalTimeout,
                maxAttempts,
                Duration.ofMillis(150));
    }
}

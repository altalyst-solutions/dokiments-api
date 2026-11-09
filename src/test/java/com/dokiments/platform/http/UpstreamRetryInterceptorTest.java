package com.dokiments.platform.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.shared.error.UpstreamUnavailableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/**
 * When a request is sent again, how long the waits are, and when the call gives up.
 *
 * <p>Time is simulated: waits and slow attempts move a fake clock forward, so these tests run instantly and the time
 * limits can be checked exactly. The random part of each wait is fixed to zero, so waits are exactly 150, 450, ... ms.
 */
class UpstreamRetryInterceptorTest {

    private static final HttpClientProperties DEFAULTS = new HttpClientProperties(
            Duration.ofSeconds(2),
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            Duration.ofSeconds(8),
            3,
            Duration.ofMillis(150));

    private static final URI ADDRESS = URI.create("https://auth.example.test/v1/accounts:signIn?key=secret");

    private long now;
    private final List<Duration> waits = new ArrayList<>();
    private final Deque<Attempt> attempts = new ArrayDeque<>();
    private int sent;

    private final UpstreamRetryInterceptor interceptor = new UpstreamRetryInterceptor(
            DEFAULTS,
            () -> now,
            duration -> {
                waits.add(duration);
                now += duration.toNanos();
            },
            () -> 0.5);

    @Test
    void aSuccessfulRequestIsSentOnce() throws IOException {
        willAnswer(HttpStatus.OK);

        try (var response = send(HttpMethod.POST)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void errorResponsesAreReturnedWithoutRetrying() throws IOException {
        // The request reached the service; whether to treat the answer as an outage is decided elsewhere.
        willAnswer(HttpStatus.SERVICE_UNAVAILABLE);

        try (var response = send(HttpMethod.GET)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void aRefusedConnectionIsRetriedWhateverTheMethod() throws IOException {
        willFail(new ConnectException("Connection refused"));
        willFail(new ConnectException("Connection refused"));
        willAnswer(HttpStatus.OK);

        try (var response = send(HttpMethod.POST)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(sent).isEqualTo(3);
        assertThat(waits).containsExactly(Duration.ofMillis(150), Duration.ofMillis(450));
    }

    @Test
    void anAddressThatCantBeResolvedIsRetried() throws IOException {
        // How the JDK client reports a DNS failure: the real reason is only in the cause.
        willFail(new ConnectException().initCause(new UnresolvedAddressException()));
        willAnswer(HttpStatus.OK);

        try (var response = send(HttpMethod.POST)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void aConnectionThatCantBeSetUpInTimeIsRetried() throws IOException {
        willFail(new HttpConnectTimeoutException("HTTP connect timed out"));
        willAnswer(HttpStatus.OK);

        try (var response = send(HttpMethod.POST)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void afterTheLastAttemptTheServiceIsReportedUnavailable() {
        var lastFailure = new ConnectException("Connection refused");
        willFail(new ConnectException("Connection refused"));
        willFail(new ConnectException("Connection refused"));
        willFail(lastFailure);

        assertThatThrownBy(() -> send(HttpMethod.GET).close())
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasCause(lastFailure);
        assertThat(sent).isEqualTo(3);
    }

    @Test
    void aTimedOutWriteIsNeverSentAgain() {
        // It may have arrived and been applied; sending it again could write twice.
        willFail(new HttpTimeoutException("Request timed out"));

        assertThatThrownBy(() -> send(HttpMethod.POST).close()).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void aTimedOutReadIsSentAgainOnce() {
        willFail(new HttpTimeoutException("Request timed out"));
        willFail(new HttpTimeoutException("Request timed out"));
        willAnswer(HttpStatus.OK);

        assertThatThrownBy(() -> send(HttpMethod.GET).close()).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void aTimedOutReadCanStillSucceedOnItsRetry() throws IOException {
        willFail(new HttpTimeoutException("Request timed out"));
        willAnswer(HttpStatus.OK);

        try (var response = send(HttpMethod.GET)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void aConnectionThatDropsAfterSendingIsNotRetried() {
        // The request may have arrived, so even a read isn't sent again.
        willFail(new IOException("Connection reset"));

        assertThatThrownBy(() -> send(HttpMethod.GET).close()).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(sent).isEqualTo(1);
    }

    @Test
    void noAttemptIsStartedThatCouldRunPastTheTotalTimeLimit() {
        // Each failed connection takes the full 2 s connect timeout. After the first (2 s), a retry still fits:
        // 2 + 0.15 wait + 5 for the attempt = 7.15 s, within 8 s. After the second (4.15 s) it no longer does.
        willFail(new HttpConnectTimeoutException("HTTP connect timed out"), Duration.ofSeconds(2));
        willFail(new HttpConnectTimeoutException("HTTP connect timed out"), Duration.ofSeconds(2));
        willAnswer(HttpStatus.OK);

        assertThatThrownBy(() -> send(HttpMethod.POST).close()).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void aWaitThatIsInterruptedGivesUpAndKeepsTheInterrupt() {
        var interrupted = new UpstreamRetryInterceptor(
                DEFAULTS,
                () -> now,
                _ -> {
                    throw new InterruptedException();
                },
                () -> 0.5);
        willFail(new ConnectException("Connection refused"));
        willAnswer(HttpStatus.OK);

        try {
            assertThatThrownBy(() -> interrupted
                            .intercept(request(HttpMethod.GET), new byte[0], execution())
                            .close())
                    .isInstanceOf(UpstreamUnavailableException.class);
            assertThat(sent).isEqualTo(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear the flag so it can't affect the next test on this thread.
            boolean stillInterrupted = Thread.interrupted();
            assertThat(stillInterrupted).isTrue();
        }
    }

    @Test
    void eachWaitIsThreeTimesTheLastGiveOrTakeAQuarter() {
        var shortest = new UpstreamRetryInterceptor(DEFAULTS, () -> 0, _ -> {}, () -> 0.0);
        var longest = new UpstreamRetryInterceptor(DEFAULTS, () -> 0, _ -> {}, () -> 0.999_999);

        assertThat(shortest.retryDelay(1)).isEqualTo(Duration.ofNanos(112_500_000));
        assertThat(shortest.retryDelay(2)).isEqualTo(Duration.ofNanos(337_500_000));
        assertThat(longest.retryDelay(1)).isBetween(Duration.ofMillis(187), Duration.ofNanos(187_500_000));
        assertThat(interceptor.retryDelay(3)).isEqualTo(Duration.ofMillis(1350));
    }

    @Test
    void addressesInTheLogLeaveOutTheQueryAndUserInformation() {
        assertThat(UpstreamRetryInterceptor.describe(URI.create("https://user:pw@api.example.test:8443/v1/a?key=s")))
                .isEqualTo("https://api.example.test:8443/v1/a");
        assertThat(UpstreamRetryInterceptor.describe(ADDRESS))
                .isEqualTo("https://auth.example.test/v1/accounts:signIn");
    }

    private ClientHttpResponse send(HttpMethod method) {
        return interceptor.intercept(request(method), new byte[0], execution());
    }

    private static MockClientHttpRequest request(HttpMethod method) {
        return new MockClientHttpRequest(method, ADDRESS);
    }

    /** Plays the scripted attempts in order, moving the clock forward by how long each one takes. */
    private ClientHttpRequestExecution execution() {
        return (_, _) -> {
            sent++;
            Attempt attempt = attempts.removeFirst();
            now += attempt.takes().toNanos();
            if (attempt.failure() != null) {
                throw attempt.failure();
            }
            return new MockClientHttpResponse(new byte[0], attempt.status());
        };
    }

    private void willAnswer(HttpStatus status) {
        attempts.add(new Attempt(null, status, Duration.ofMillis(100)));
    }

    private void willFail(Throwable failure) {
        willFail(failure, Duration.ofMillis(10));
    }

    /** Takes a {@code Throwable} because {@code initCause} returns one; every failure here is an I/O failure. */
    private void willFail(Throwable failure, Duration takes) {
        attempts.add(new Attempt((IOException) failure, HttpStatus.OK, takes));
    }

    private record Attempt(@Nullable IOException failure, HttpStatus status, Duration takes) {}
}

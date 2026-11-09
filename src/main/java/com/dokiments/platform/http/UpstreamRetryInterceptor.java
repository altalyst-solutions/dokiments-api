package com.dokiments.platform.http;

import com.dokiments.shared.error.UpstreamUnavailableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Sends a request again when that is certainly safe, and turns a service that can't be reached into
 * {@link UpstreamUnavailableException}.
 *
 * <p>Retrying is deliberately narrow. A request is only sent again when:
 *
 * <ul>
 *   <li>it never left this process: the address couldn't be resolved, the connection was refused, or no connection
 *       could be set up in time. The service never saw it, so whatever the method, sending it again can't do anything
 *       twice.
 *   <li>or it only reads ({@code GET}, {@code HEAD}) and timed out. Reading twice is harmless, so this is retried once.
 * </ul>
 *
 * <p>Everything else is not retried, because the request may already have reached the service: any response, even
 * {@code 500} or {@code 429}; a connection that dropped after the request was sent; a timed-out {@code POST}. Sending
 * those again could, for example, spend a single-use sign-in code twice or write data twice.
 *
 * <p>Responses are returned as they are. Turning an error response into an exception is the job of the status handler
 * in {@link HttpClientConfiguration}.
 */
final class UpstreamRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UpstreamRetryInterceptor.class);

    /** A timed-out read is sent again at most this many times, on top of retries for failed connections. */
    private static final int MAX_TIMEOUT_RETRIES = 1;

    private static final double RETRY_DELAY_MULTIPLIER = 3;

    /** Each wait is randomly up to this fraction longer or shorter. */
    private static final double RETRY_DELAY_JITTER = 0.25;

    /** Waits between attempts. A separate type so tests can wait without sleeping. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final HttpClientProperties properties;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final DoubleSupplier random;

    UpstreamRetryInterceptor(HttpClientProperties properties) {
        this(
                properties,
                System::nanoTime,
                Thread::sleep,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * For tests: {@code nanoTime} is the clock, {@code sleeper} waits, and {@code random} returns a number from 0
     * (inclusive) to 1 (exclusive) for the random part of each wait.
     */
    UpstreamRetryInterceptor(
            HttpClientProperties properties, LongSupplier nanoTime, Sleeper sleeper, DoubleSupplier random) {
        this.properties = properties;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
        this.random = random;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        boolean readOnly = UpstreamRequestFactory.isReadOnly(request.getMethod());
        Duration attemptTimeout = readOnly ? properties.readAttemptTimeout() : properties.attemptTimeout();
        long startedAt = nanoTime.getAsLong();
        int timeoutRetries = 0;

        for (int attempt = 1; ; attempt++) {
            IOException failure;
            try {
                return execution.execute(request, body);
            } catch (IOException e) {
                failure = e;
            }

            boolean timedOutRead = readOnly && isTimeout(failure) && timeoutRetries < MAX_TIMEOUT_RETRIES;
            boolean retryable = !Thread.currentThread().isInterrupted() && (neverSent(failure) || timedOutRead);
            Duration delay = retryDelay(attempt);
            Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - startedAt);
            boolean fitsInTime = elapsed.plus(delay).plus(attemptTimeout).compareTo(properties.totalTimeout()) <= 0;

            if (!retryable || attempt >= properties.maxAttempts() || !fitsInTime) {
                throw new UpstreamUnavailableException(failure);
            }
            if (timedOutRead) {
                timeoutRetries++;
            }

            log.warn(
                    "{} {} failed with {}; retrying in {} ms (attempt {} of {})",
                    request.getMethod(),
                    describe(request.getURI()),
                    failure.getClass().getSimpleName(),
                    delay.toMillis(),
                    attempt + 1,
                    properties.maxAttempts());
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                // Keep the interrupt for whoever stops this thread (usually a shutdown), and give up.
                Thread.currentThread().interrupt();
                failure.addSuppressed(e);
                throw new UpstreamUnavailableException(failure);
            }
        }
    }

    /**
     * Whether the failure proves the request never reached the service. Checked along the causes, because the HTTP
     * client wraps them differently depending on where the connection failed.
     */
    static boolean neverSent(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConnectException
                    || t instanceof HttpConnectTimeoutException
                    || t instanceof UnknownHostException
                    || t instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    /** The attempt was sent but no complete answer arrived in time. */
    private static boolean isTimeout(Throwable failure) {
        return failure instanceof HttpTimeoutException && !(failure instanceof HttpConnectTimeoutException);
    }

    /** The wait before attempt {@code attempt + 1}: the first delay, tripled for each attempt so far, give or take. */
    Duration retryDelay(int attempt) {
        double base = properties.firstRetryDelay().toNanos() * Math.pow(RETRY_DELAY_MULTIPLIER, attempt - 1);
        double jitter = 1 + (random.getAsDouble() * 2 - 1) * RETRY_DELAY_JITTER;
        return Duration.ofNanos(Math.round(base * jitter));
    }

    /**
     * The address for the log, without the query and user information. Some services expect a key in the query, and it
     * must never be written to the logs.
     */
    static String describe(URI uri) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return uri.getScheme() + "://" + uri.getHost() + port + path;
    }
}

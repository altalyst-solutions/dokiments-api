package com.dokiments.platform.http;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Timeouts and retries for calls to external services, under {@code dokiments.http}.
 *
 * <p>The defaults suit the services this application calls and rarely need changing. They exist as settings mainly so
 * tests can use much shorter values.
 *
 * @param connectTimeout how long to wait for a connection to be set up
 * @param readAttemptTimeout how long one attempt of a {@code GET} or {@code HEAD} may take, from sending the request
 *     until the whole response has been read. Shorter than for other requests, because a read that is stuck is safe to
 *     abandon and send again.
 * @param attemptTimeout the same for every other request. Well above the normal response time of these services (a slow
 *     but successful call can take close to 3 seconds), so it only ends a connection that is really stuck.
 * @param totalTimeout upper limit for one call including every retry and the waits between them. A new attempt is only
 *     started if it can finish within this limit, so the caller can count on an answer in time to report {@code 503}.
 * @param maxAttempts how many times a request is sent at most, the first attempt included
 * @param firstRetryDelay the wait before the first retry. Each following wait is three times longer, give or take a
 *     random quarter, so that many requests failing at once don't all retry at the same moment.
 */
@ConfigurationProperties("dokiments.http")
public record HttpClientProperties(
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readAttemptTimeout,
        @DefaultValue("5s") Duration attemptTimeout,
        @DefaultValue("8s") Duration totalTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("150ms") Duration firstRetryDelay) {

    public HttpClientProperties {
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readAttemptTimeout, "read-attempt-timeout");
        requirePositive(attemptTimeout, "attempt-timeout");
        requirePositive(totalTimeout, "total-timeout");
        if (firstRetryDelay.isNegative()) {
            throw new IllegalArgumentException("dokiments.http.first-retry-delay can't be negative.");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("dokiments.http.max-attempts must be at least 1.");
        }
        // Otherwise a single attempt could run past the limit for the whole call.
        if (attemptTimeout.compareTo(totalTimeout) > 0 || readAttemptTimeout.compareTo(totalTimeout) > 0) {
            throw new IllegalArgumentException(
                    "dokiments.http.total-timeout must be at least as long as each attempt timeout.");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("dokiments.http." + name + " must be longer than zero.");
        }
    }
}

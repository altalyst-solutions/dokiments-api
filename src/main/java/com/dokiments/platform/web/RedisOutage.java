package com.dokiments.platform.web;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Whether a failure means Redis couldn't be used: unreachable, slower than {@code spring.data.redis.timeout}, or
 * refusing commands, as a hosted Redis does past its plan's limit. Like any external service that can't be reached,
 * that is answered with {@code 503}, never {@code 500}.
 *
 * <p>Spring Data Redis reports these as {@link RedisConnectionFailureException}, {@link QueryTimeoutException} (a
 * command that timed out) and {@link RedisSystemException} (an error Redis answered). Nothing else in the application
 * uses Spring's data access exceptions, so they always come from Redis here.
 */
public final class RedisOutage {

    /**
     * Where {@code RedisOutageFilter} sits: just outside Spring Session's filter, which reads and writes the session,
     * so it sees every Redis failure in a request. A filter that answers some routes differently goes between the two.
     */
    public static final int FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER - 10;

    /** How far down a chain of causes to look. Real chains are a few levels deep; this also ends a looping one. */
    private static final int MAX_CAUSES = 16;

    private RedisOutage() {}

    /** Whether the failure, or anything that caused it, is a Redis outage. */
    public static boolean causedBy(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisSystemException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

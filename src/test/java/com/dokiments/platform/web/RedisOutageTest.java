package com.dokiments.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/** Which failures count as Redis being unavailable, also when wrapped in other exceptions on the way up. */
class RedisOutageTest {

    @Test
    void redisThatCantBeReachedIsTooSlowOrRefusesCommandsIsAnOutage() {
        assertThat(RedisOutage.causedBy(new RedisConnectionFailureException("Unable to connect")))
                .isTrue();
        assertThat(RedisOutage.causedBy(new QueryTimeoutException("Command timed out")))
                .isTrue();
        assertThat(RedisOutage.causedBy(new RedisSystemException("ERR max requests limit exceeded", null)))
                .isTrue();
    }

    @Test
    void anOutageIsFoundUnderTheExceptionsThatWrapIt() {
        var wrapped = new ServletException(
                "Request processing failed", new IllegalStateException(new QueryTimeoutException("timed out")));

        assertThat(RedisOutage.causedBy(wrapped)).isTrue();
    }

    @Test
    void otherFailuresAreNotAnOutage() {
        assertThat(RedisOutage.causedBy(new IllegalStateException("a bug"))).isFalse();
        assertThat(RedisOutage.causedBy(new DataIntegrityViolationException("not from Redis")))
                .isFalse();
    }

    @Test
    void aChainOfCausesThatLoopsEnds() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second", first);
        first.initCause(second);

        assertThat(RedisOutage.causedBy(first)).isFalse();
    }
}

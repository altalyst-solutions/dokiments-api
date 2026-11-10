package com.dokiments.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Which requests are recorded. */
class ObservabilityConfigurationTest {

    private final ObservationPredicate predicate = new ObservabilityConfiguration().skipActuatorRequests();

    @Test
    void actuatorRequestsAreSkipped() {
        assertThat(predicate.test("http.server.requests", serverRequest("/actuator/health/readiness")))
                .isFalse();
        assertThat(predicate.test("http.server.requests", serverRequest("/actuator")))
                .isFalse();
    }

    @Test
    void everythingElseIsRecorded() {
        assertThat(predicate.test("http.server.requests", serverRequest("/api/documents")))
                .isTrue();
        assertThat(predicate.test("http.server.requests", serverRequest("/actuatorish")))
                .isTrue();
        assertThat(predicate.test("http.client.requests", new Observation.Context()))
                .isTrue();
    }

    private static ServerRequestObservationContext serverRequest(String path) {
        return new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", path), new MockHttpServletResponse());
    }
}

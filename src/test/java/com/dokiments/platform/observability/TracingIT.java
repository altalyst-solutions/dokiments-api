package com.dokiments.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import com.google.cloud.firestore.Firestore;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Every request gets a trace, its ID is returned, and Firestore calls join the same trace. */
@IntegrationTest
@AutoConfigureMockMvc
class TracingIT {

    private static final String TRACE_ID = "[0-9a-f]{32}";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Firestore firestore;

    @Autowired
    private OpenTelemetry openTelemetry;

    @Test
    void everyResponseCarriesItsTraceIdEvenWhenRejected() {
        // Rejected by Spring Security, which runs after the header is set.
        assertThat(mvc.get().uri("/api/anything"))
                .hasStatus(401)
                .headers()
                .hasHeaderSatisfying(
                        TraceIdResponseHeaderFilter.HEADER,
                        values -> assertThat(values).singleElement().asString().matches(TRACE_ID));
    }

    @Test
    void eachRequestHasItsOwnTrace() {
        String first = traceIdOf(
                mvc.get().uri("/api/anything").exchange().getResponse().getHeader(TraceIdResponseHeaderFilter.HEADER));
        String second = traceIdOf(
                mvc.get().uri("/api/anything").exchange().getResponse().getHeader(TraceIdResponseHeaderFilter.HEADER));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aRequestThatArrivesWithATraceContinuesIt() {
        // As a load balancer or the web app would send it (W3C Trace Context).
        assertThat(mvc.get()
                        .uri("/api/anything")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .headers()
                .hasValue(TraceIdResponseHeaderFilter.HEADER, "4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void healthChecksArentTraced() {
        assertThat(mvc.get().uri("/actuator/health/liveness"))
                .hasStatusOk()
                .headers()
                .doesNotContainHeader(TraceIdResponseHeaderFilter.HEADER);
    }

    @Test
    void firestoreCallsAreTracedWithTheApplicationsSetup() {
        assertThat(firestore.getOptions().getOpenTelemetryOptions().getOpenTelemetry())
                .isSameAs(openTelemetry);
    }

    private static String traceIdOf(String header) {
        assertThat(header).matches(TRACE_ID);
        return header;
    }
}

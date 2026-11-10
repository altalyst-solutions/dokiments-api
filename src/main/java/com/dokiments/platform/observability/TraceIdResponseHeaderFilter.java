package com.dokiments.platform.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@code X-Trace-Id} to every response: the ID of the request's trace, which every log line written while handling
 * it also carries ({@code trace.id} in the JSON logs).
 *
 * <p>When someone reports a problem with a request, the ID from their browser's network tab leads straight to its log
 * lines and its trace. It identifies the request and nothing else, so it is safe to show. If the request arrived with a
 * {@code traceparent} header, for example from the platform's load balancer, the trace continues that one.
 */
final class TraceIdResponseHeaderFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Trace-Id";

    private final ObjectProvider<Tracer> tracer;

    TraceIdResponseHeaderFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Set before the request is handled: once the body is written, headers can no longer be added.
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        if (span != null) {
            response.setHeader(HEADER, span.context().traceId());
        }
        chain.doFilter(request, response);
    }
}

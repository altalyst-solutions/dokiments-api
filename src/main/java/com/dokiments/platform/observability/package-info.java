/**
 * What the application tells operators about itself: traces, metrics, and the trace ID that ties a response to its log
 * lines.
 *
 * <p>Spring Boot does most of the work: every request, Redis command, Firestore call and call to an external service is
 * recorded, and every log line written while handling a request carries its trace ID. Traces and metrics are exported
 * with OTLP, the OpenTelemetry protocol, to whichever collector the platform provides, configured with the standard
 * {@code OTEL_*} environment variables. Without them, nothing is exported.
 */
@NullMarked
package com.dokiments.platform.observability;

import org.jspecify.annotations.NullMarked;

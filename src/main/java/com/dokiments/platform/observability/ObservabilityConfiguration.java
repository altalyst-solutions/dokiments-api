package com.dokiments.platform.observability;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** Adjusts what Spring Boot records, and adds the trace ID to every response. */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfiguration {

    /**
     * Requests to {@code /actuator} aren't traced or measured. The platform's health probes call it every few seconds,
     * which would bury real requests and cost money wherever traces are stored.
     */
    @Bean
    ObservationPredicate skipActuatorRequests() {
        return (name, context) ->
                !(context instanceof ServerRequestObservationContext request && isActuator(request.getCarrier()));
    }

    static boolean isActuator(@Nullable HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }

    /**
     * Runs right after Spring Boot's filter that starts the request's trace, so the trace exists, and before Spring
     * Security, so even a rejected request gets the header.
     */
    @Bean
    FilterRegistrationBean<TraceIdResponseHeaderFilter> traceIdResponseHeaderFilter(ObjectProvider<Tracer> tracer) {
        var registration = new FilterRegistrationBean<>(new TraceIdResponseHeaderFilter(tracer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}

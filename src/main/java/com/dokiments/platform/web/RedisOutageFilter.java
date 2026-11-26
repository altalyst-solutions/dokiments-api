package com.dokiments.platform.web;

import com.dokiments.shared.error.UpstreamUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers {@code 503} when Redis can't be used, wherever in the request that happens.
 *
 * <p>The session is read from Redis in the security filters and written back by Spring Session's filter after the
 * controller has answered, so a Redis outage mostly fails outside Spring MVC, where {@link ApiExceptionHandler} never
 * sees it. The servlet container would then answer {@code 500} through Spring Boot's error page, which reads the
 * session again on its way and waits on Redis a second time. This filter wraps Spring Session's, so it catches those
 * failures and answers as for any unreachable service.
 *
 * <p>Only the response body is cleared, not its headers, so the answer keeps its {@code X-Trace-Id}.
 */
final class RedisOutageFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RedisOutageFilter.class);

    private final ErrorResponseWriter errors;

    RedisOutageFilter(ErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (!RedisOutage.causedBy(e)) {
                throw e;
            }
            log.warn("Redis couldn't be used; answering 503", e);
            if (response.isCommitted()) {
                return;
            }
            response.resetBuffer();
            errors.write(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, UpstreamUnavailableException.DEFAULT_MESSAGE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Registration {

        @Bean
        FilterRegistrationBean<RedisOutageFilter> redisOutageFilter(ErrorResponseWriter errors) {
            var registration = new FilterRegistrationBean<>(new RedisOutageFilter(errors));
            registration.setOrder(RedisOutage.FILTER_ORDER);
            return registration;
        }
    }
}

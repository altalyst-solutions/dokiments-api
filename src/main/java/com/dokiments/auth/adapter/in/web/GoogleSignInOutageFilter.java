package com.dokiments.auth.adapter.in.web;

import com.dokiments.platform.web.RedisOutage;
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
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sends the browser back to the sign-in page when Redis fails during a Google sign-in route.
 *
 * <p>Those routes are page loads, not calls from JavaScript, so they redirect on any failure (see
 * {@link GoogleSignInController}). Inside the controller that is handled already. The session, though, is read in the
 * security filters before the controller runs, and written back by Spring Session's filter after it. A Redis outage
 * there would otherwise reach the platform's {@code RedisOutageFilter}, whose {@code 503} body would be shown as a
 * page. This filter sits between the two, for these routes only.
 */
final class GoogleSignInOutageFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GoogleSignInOutageFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (!RedisOutage.causedBy(e)) {
                throw e;
            }
            log.warn("Google sign-in failed: Redis couldn't be used", e);
            if (response.isCommitted()) {
                return;
            }
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader(HttpHeaders.LOCATION, GoogleSignInController.SIGN_IN_FAILED.toString());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Registration {

        @Bean
        FilterRegistrationBean<GoogleSignInOutageFilter> googleSignInOutageFilter() {
            var registration = new FilterRegistrationBean<>(new GoogleSignInOutageFilter());
            registration.addUrlPatterns("/api/auth/google/*");
            // Inside the platform's filter, so it answers these routes first, and still outside Spring Session's.
            registration.setOrder(RedisOutage.FILTER_ORDER + 1);
            return registration;
        }
    }
}

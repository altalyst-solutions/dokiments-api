package com.dokiments.platform.security;

import com.dokiments.platform.web.ErrorMessages;
import com.dokiments.platform.web.ErrorResponseWriter;
import com.dokiments.shared.error.UpstreamUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asks each {@link RequestAuthenticator} who the request is from, and records the answer for the security rules and the
 * controllers.
 *
 * <p>The answer only lives for this request: it is worked out again from the session on every request, so a session
 * ended elsewhere (signed out, expired) stops working at once.
 */
final class RequestAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAuthenticationFilter.class);

    private final List<RequestAuthenticator> authenticators;
    private final ErrorResponseWriter errorResponseWriter;

    RequestAuthenticationFilter(List<RequestAuthenticator> authenticators, ErrorResponseWriter errorResponseWriter) {
        this.authenticators = authenticators;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (RequestAuthenticator authenticator : authenticators) {
            try {
                var authentication = authenticator.authenticate(request, response);
                if (authentication != null) {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                    break;
                }
            } catch (UpstreamUnavailableException e) {
                // The session may well be valid; it just couldn't be checked. The client can try again, and the session
                // is kept. Exceptions thrown from a filter would otherwise reach the client as a 500.
                log.warn("External service unavailable while checking the session", e);
                errorResponseWriter.write(
                        response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, ErrorMessages.UPSTREAM_UNAVAILABLE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

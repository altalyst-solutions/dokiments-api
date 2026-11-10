package com.dokiments.platform.security;

import com.dokiments.platform.web.ErrorMessages;
import com.dokiments.platform.web.ErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security, until authentication is implemented.
 *
 * <p>Only the health and info endpoints and the public endpoints listed below are open. Every other request is answered
 * with {@code 401}, which means nothing becomes reachable by accident while the API is being built. Endpoints are
 * opened explicitly, one by one, as features land.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ErrorResponseWriter errorResponseWriter) {
        return http.authorizeHttpRequests(requests -> requests
                        // When something fails outside a controller, the servlet container forwards the request
                        // internally to Spring Boot's /error page to write the response. That forward has to be
                        // allowed, or the deny-all rule below would replace the real status (say a 500) with a
                        // misleading 401. It isn't a way in: clients can't make an ERROR dispatch themselves.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // Public newsletter sign-up from the website.
                        .requestMatchers(HttpMethod.POST, "/api/newsletter")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // Cross-site request forgery tricks a signed-in browser into sending a request with its
                // cookies. Public endpoints that use no session can't be abused that way, so they don't need a
                // CSRF token, which the web app doesn't send.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/newsletter"))
                // No browser login page or Basic auth dialog: an API answers with a status code.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // The request cache saves a rejected request in the session to "resume" it after a
                // browser login. An API never does that, and it would create a Redis session for every
                // anonymous request, so anyone could fill Redis just by calling protected URLs.
                .requestCache(AbstractHttpConfigurer::disable)
                // Rejections get the same { "error": "..." } body as every other error, with the wording the
                // web app expects: 401 when the caller isn't signed in, 403 when they are but may not do this.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((_, response, _) -> errorResponseWriter.write(
                                response, HttpServletResponse.SC_UNAUTHORIZED, ErrorMessages.UNAUTHENTICATED))
                        .accessDeniedHandler((_, response, _) -> errorResponseWriter.write(
                                response, HttpServletResponse.SC_FORBIDDEN, ErrorMessages.FORBIDDEN)))
                .build();
    }
}

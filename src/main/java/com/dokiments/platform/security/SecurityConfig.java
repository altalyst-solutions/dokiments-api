package com.dokiments.platform.security;

import com.dokiments.platform.web.ErrorMessages;
import com.dokiments.platform.web.ErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

/**
 * Deny-by-default security.
 *
 * <p>Only the health and info endpoints and the public endpoints listed below are open. Every other request is answered
 * with {@code 401}, which means nothing becomes reachable by accident while the API is being built. Endpoints are
 * opened explicitly, one by one, as features land.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ErrorResponseWriter errorResponseWriter,
            ObjectProvider<RequestAuthenticator> authenticators) {
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
                        // Signing in, up and out need no session. /me answers anyone: with the user, or with 401
                        // and the body the web app expects for "not signed in".
                        .requestMatchers(
                                HttpMethod.POST, "/api/auth/sign-up", "/api/auth/sign-in", "/api/auth/sign-out")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me")
                        .permitAll()
                        // Google sign-in: browser redirects, protected by their own state check.
                        .requestMatchers(HttpMethod.GET, "/api/auth/google/start", "/api/auth/google/callback")
                        .permitAll()
                        // A reset link can only be asked for without a session: whoever needs one can't sign in.
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password")
                        .permitAll()
                        // Changes to one's own account.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/update-password",
                                "/api/auth/reauthenticate",
                                "/api/auth/update-profile",
                                "/api/auth/send-verification",
                                "/api/auth/refresh-verification")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                // Cross-site request forgery tricks a signed-in browser into sending a request with its cookies.
                // Every request that changes something must carry a token that another site can't read: the
                // token is in the readable XSRF-TOKEN cookie, and the web app sends it back in the X-XSRF-TOKEN
                // header (Spring's setup for single-page apps). Signing in and up are covered too, so another
                // site can't sign a visitor into an account of its choosing. The newsletter sign-up uses no
                // session at all, so there is nothing to forge.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(CsrfExemptions.PATHS.toArray(String[]::new)))
                // Who sent the request comes from the session on every request (see RequestAuthenticator), so
                // Spring Security keeps nothing in the session itself and never creates one: only signing in does.
                .securityContext(
                        context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.NEVER)
                        // Spring Security would take the user set on every request for a fresh sign-in and give the
                        // session a new ID each time. Signing in does that itself, once, where it belongs.
                        .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::none))
                .addFilterBefore(
                        new RequestAuthenticationFilter(
                                authenticators.orderedStream().toList(), errorResponseWriter),
                        AnonymousAuthenticationFilter.class)
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

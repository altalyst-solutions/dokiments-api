package com.dokiments.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Deny-by-default security, until authentication is implemented.
 *
 * <p>Only the health and info endpoints are public, so container platforms can probe the service. Every other request
 * is answered with {@code 401}, which means nothing becomes reachable by accident while the API is being built.
 * Endpoints are opened explicitly, one by one, as features land.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.authorizeHttpRequests(requests -> requests.requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // No browser login page or Basic auth dialog: an API answers with a status code.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // The request cache saves a rejected request in the session to "resume" it after a
                // browser login. An API never does that, and it would create a Redis session for every
                // anonymous request, so anyone could fill Redis just by calling protected URLs.
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}

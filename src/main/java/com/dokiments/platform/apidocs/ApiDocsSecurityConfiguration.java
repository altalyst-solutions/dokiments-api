package com.dokiments.platform.apidocs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the API description and Swagger UI where they are switched on: {@code springdoc.api-docs.enabled=true}, which
 * is the {@code dev} profile.
 *
 * <p>It is a separate filter chain, limited to their paths, so the main deny-by-default rules in
 * {@code platform.security} stay as they are. Where the description is off, this chain doesn't exist, and those paths
 * are closed like any other.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
class ApiDocsSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) {
        return http.securityMatcher(
                        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Reading documentation creates nothing and changes nothing: no session, no cookie.
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }
}

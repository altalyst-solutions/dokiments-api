package com.dokiments.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import com.dokiments.support.TestcontainersConfiguration;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks errors that happen before any controller is reached, against a real running server.
 *
 * <p>A test filter fails on purpose. The servlet container then forwards the request internally to Spring Boot's
 * {@code /error} page, which only produces the API's error body if two things work together: the security configuration
 * lets that internal forward through, and {@link ApiErrorAttributes} replaces Spring Boot's default body. MockMvc
 * doesn't perform that forward, which is why this needs a real server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ErrorsOutsideControllersIT.FailingFilterConfiguration.class})
@ActiveProfiles("test")
@Tag(IntegrationTest.TAG)
class ErrorsOutsideControllersIT {

    private static final String FAILING_PATH = "/probe/filter-failure";

    @LocalServerPort
    private int port;

    @Test
    void aFailureInAFilterIsAnsweredWithTheGenericErrorBody() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + FAILING_PATH))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        // Without the security exception for internal error forwards, this would be a misleading 401.
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).isEqualTo("{\"error\":\"Something went wrong. Please try again.\"}");
        assertThat(response.body()).doesNotContain("internal detail");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingFilterConfiguration {

        /** Runs before Spring Security, so the request fails before it is even checked. */
        @Bean
        FilterRegistrationBean<Filter> failingFilter() {
            Filter filter = (request, response, chain) -> {
                if (FAILING_PATH.equals(((HttpServletRequest) request).getRequestURI())) {
                    throw new IllegalStateException("internal detail that must not reach the client");
                }
                chain.doFilter(request, response);
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}

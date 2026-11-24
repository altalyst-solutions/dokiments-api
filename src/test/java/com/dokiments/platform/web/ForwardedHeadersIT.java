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
 * Checks that the API sees a request as the browser sent it, not as the proxies in front of it pass it on.
 *
 * <p>Deployed, requests reach the API through Next.js and the platform's load balancer, over plain HTTP inside the
 * platform, with the original scheme and host in {@code X-Forwarded-*} headers. The API has to trust those headers
 * ({@code server.request.header.forwarded.strategy} in application.yml), or it would treat every request as plain HTTP
 * to its own address. A test filter, placed after the one that reads the headers, answers with what the application
 * sees. It needs a real server, because the headers are applied by a servlet filter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ForwardedHeadersIT.ProbeFilterConfiguration.class})
@ActiveProfiles("test")
@Tag(IntegrationTest.TAG)
class ForwardedHeadersIT {

    private static final String PROBE_PATH = "/probe/forwarded-headers";

    @LocalServerPort
    private int port;

    @Test
    void theOriginalSchemeAndHostFromTheProxyAreUsed() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + PROBE_PATH))
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "dokiments.com")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.body()).isEqualTo("https://dokiments.com secure=true");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeFilterConfiguration {

        /**
         * Runs right after Spring's forwarded-header filter, which has the highest precedence, and before Spring
         * Security, so the probe needs no session.
         */
        @Bean
        FilterRegistrationBean<Filter> forwardedHeadersProbe() {
            Filter filter = (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                if (!PROBE_PATH.equals(http.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }
                response.setContentType("text/plain");
                response.getWriter()
                        .write(http.getScheme() + "://" + http.getServerName() + " secure=" + http.isSecure());
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            return registration;
        }
    }
}

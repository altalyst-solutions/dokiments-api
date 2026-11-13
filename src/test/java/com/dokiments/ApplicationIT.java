package com.dokiments;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Smoke test of the running skeleton: it starts against real Redis, reports healthy, exposes only what it should, and
 * rejects everything else without creating sessions.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApplicationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        var connectionFactory = redis.getConnectionFactory();
        assertThat(connectionFactory).isNotNull();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Test
    void healthIsUpIncludingRedis() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void probesAreUp(String probe) {
        assertThat(mvc.get().uri(probe))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void healthHidesComponentDetails() {
        assertThat(mvc.get().uri("/actuator/health")).bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void infoReportsVersionCommitAndJava() {
        var info = assertThat(mvc.get().uri("/actuator/info")).hasStatusOk().bodyJson();

        info.extractingPath("$.build.version").asString().isNotBlank();
        info.extractingPath("$.build.commit").asString().isNotBlank();
        info.extractingPath("$.java.version").asString().startsWith("25");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/api/documents",
                "/api/auth/me",
                "/actuator/env",
                "/actuator/beans",
                // The API description and Swagger UI are only switched on in the dev profile.
                "/v3/api-docs",
                "/v3/api-docs.yaml",
                "/swagger-ui.html",
                "/swagger-ui/index.html"
            })
    void everythingElseIsUnauthorisedWithoutCreatingASession(String path) {
        var result = mvc.get().uri(path).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        // Only the CSRF token's cookie, which every response may set; never a session cookie.
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("dokiments-session"));

        assertThat(redis.keys("*"))
                .as("a rejected anonymous request must not leave a session in Redis")
                .isEmpty();
    }

    @Test
    void anAnonymousRequestGetsTheUnauthorisedBodyTheWebAppExpects() {
        assertThat(mvc.get().uri("/api/documents"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Unauthorised.\"}");
    }

    @Test
    @WithMockUser
    void aSignedInUserIsForbiddenFromEndpointsThatArentOpenYet() {
        assertThat(mvc.get().uri("/api/documents"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Forbidden.\"}");
    }
}

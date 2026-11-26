package com.dokiments.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.support.IntegrationTest;
import com.dokiments.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

/**
 * What the API answers while Redis doesn't respond, against a real running server.
 *
 * <p>Redis is paused, which is what a hung or unreachable hosted Redis looks like to the API: connections stay open and
 * nothing comes back. A request with a session cookie makes the API read the session from Redis before any controller
 * runs. Without the command timeout, it would wait a minute; without {@link RedisOutageFilter}, it would end as a
 * {@code 500} from the servlet container's error page, which waits on Redis once more.
 *
 * <p>The test pauses this context's own Redis container and is the only one using that context
 * ({@link DirtiesContext}), so no other test sees the outage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Tag(IntegrationTest.TAG)
@DirtiesContext
class RedisOutageIT {

    /** Any value: the API looks it up in Redis before deciding it's not a session. */
    private static final String SESSION_COOKIE = "dokiments-session=bm90LWEtc2Vzc2lvbg";

    /** Well above the 2-second command timeout, and far below the minute the default would wait. */
    private static final Duration ANSWERED_WITHIN = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    @Autowired
    private GenericContainer<?> redisContainer;

    private boolean paused;

    @AfterEach
    void resumeRedis() {
        if (paused) {
            try (var unpause = redisContainer.getDockerClient().unpauseContainerCmd(redisContainer.getContainerId())) {
                unpause.exec();
            }
            paused = false;
        }
    }

    @Test
    void aRequestThatNeedsTheSessionIsAnsweredWith503Quickly() throws Exception {
        pauseRedis();

        var response = get("/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body())
                .isEqualTo("{\"error\":\"Couldn't reach an external service. Please try again in a moment.\"}");
    }

    @Test
    void googleSignInStillRedirectsToTheSignInPage() throws Exception {
        pauseRedis();

        var response = get("/api/auth/google/start");

        // A page load: never an error body.
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("/sign-in?error=google_failed");
    }

    @Test
    void theApiRecoversOnceRedisIsBack() throws Exception {
        pauseRedis();
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(503);
        resumeRedis();

        // The made-up session isn't found, so the caller simply isn't signed in.
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    private void pauseRedis() {
        try (var pause = redisContainer.getDockerClient().pauseContainerCmd(redisContainer.getContainerId())) {
            pause.exec();
        }
        paused = true;
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Cookie", SESSION_COOKIE)
                            .timeout(ANSWERED_WITHIN)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}

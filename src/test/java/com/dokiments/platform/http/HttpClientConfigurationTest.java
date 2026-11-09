package com.dokiments.platform.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.shared.error.UpstreamUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * A {@link RestClient} built from Spring Boot's builder, as an adapter builds one, against a real HTTP server on this
 * machine. Checks that the rules really reach the client and behave as expected on the network, with short timeouts so
 * the slow cases stay quick.
 */
class HttpClientConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMessageConvertersAutoConfiguration.class,
                    HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class,
                    RestClientAutoConfiguration.class))
            .withUserConfiguration(HttpClientConfiguration.class, PropertiesConfiguration.class)
            .withPropertyValues(
                    "dokiments.http.connect-timeout=500ms",
                    "dokiments.http.read-attempt-timeout=200ms",
                    "dokiments.http.attempt-timeout=300ms",
                    "dokiments.http.total-timeout=2s",
                    "dokiments.http.first-retry-delay=10ms");

    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serve("/ok", exchange -> answer(exchange, 200, "hello"));
        serve("/outage", exchange -> answer(exchange, 503, "down"));
        serve("/busy", exchange -> answer(exchange, 429, "slow down"));
        serve("/missing", exchange -> answer(exchange, 404, "no such thing"));
        serve("/slow", exchange -> {
            stall();
            answer(exchange, 200, "too late");
        });
        serve("/slow-body", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            stall();
            exchange.close();
        });
        // One thread per request, so a slow answer doesn't hold up the next attempt.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aSuccessfulAnswerIsReturned() {
        context.run(app -> assertThat(client(app).get().uri("/ok").retrieve().body(String.class))
                .isEqualTo("hello"));
    }

    @Test
    void aServiceThatIsDownIsReportedUnavailable() {
        context.run(app -> assertThatThrownBy(
                        () -> client(app).get().uri("/outage").retrieve().toBodilessEntity())
                .isInstanceOf(UpstreamUnavailableException.class)
                .cause()
                .isInstanceOf(UpstreamErrorResponseException.class)
                .hasMessageEndingWith("/outage answered 503"));
        assertThat(hitsOf("/outage")).isEqualTo(1);
    }

    @Test
    void aServiceAskingToSlowDownIsReportedUnavailable() {
        context.run(app -> assertThatThrownBy(
                        () -> client(app).post().uri("/busy").retrieve().toBodilessEntity())
                .isInstanceOf(UpstreamUnavailableException.class));
    }

    @Test
    void otherErrorAnswersAreLeftToTheAdapter() {
        context.run(app -> assertThatThrownBy(
                        () -> client(app).get().uri("/missing").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class));
    }

    @Test
    void anAdapterCanHandleAnOutageAnswerItself() {
        context.run(app -> assertThatThrownBy(() -> client(app)
                        .get()
                        .uri("/outage")
                        .retrieve()
                        .onStatus(status -> status.value() == 503, (_, _) -> {
                            throw new IllegalStateException("handled by the adapter");
                        })
                        .toBodilessEntity())
                .hasMessage("handled by the adapter"));
    }

    @Test
    void aSlowReadIsSentOnceMoreThenReportedUnavailable() {
        context.run(app -> assertThatThrownBy(
                        () -> client(app).get().uri("/slow").retrieve().toBodilessEntity())
                .isInstanceOf(UpstreamUnavailableException.class)
                .cause()
                .isInstanceOf(HttpTimeoutException.class));
        assertThat(hitsOf("/slow")).isEqualTo(2);
    }

    @Test
    void aSlowWriteIsNeverSentTwice() {
        context.run(app -> assertThatThrownBy(() ->
                        client(app).post().uri("/slow").body("data").retrieve().toBodilessEntity())
                .isInstanceOf(UpstreamUnavailableException.class));
        assertThat(hitsOf("/slow")).isEqualTo(1);
    }

    @Test
    void aBodyThatArrivesTooSlowlyCountsAsATimeout() {
        // The attempt timeout covers reading the whole response, not just its start.
        context.run(app -> assertThatThrownBy(
                        () -> client(app).get().uri("/slow-body").retrieve().body(String.class))
                .isInstanceOf(UpstreamUnavailableException.class)
                .cause()
                .isInstanceOf(HttpTimeoutException.class));
        assertThat(hitsOf("/slow-body")).isEqualTo(2);
    }

    @Test
    void aServiceThatRefusesConnectionsIsReportedUnavailable() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        String address = "http://127.0.0.1:" + closedPort;

        context.run(app -> assertThatThrownBy(() -> app.getBean(RestClient.Builder.class)
                        .baseUrl(address)
                        .build()
                        .post()
                        .uri("/anything")
                        .retrieve()
                        .toBodilessEntity())
                .isInstanceOf(UpstreamUnavailableException.class)
                .cause()
                .isInstanceOf(ConnectException.class));
    }

    private RestClient client(ApplicationContext app) {
        return app.getBean(RestClient.Builder.class)
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
    }

    /** Serves {@code path}, counting each request as it arrives. */
    private void serve(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            hits.computeIfAbsent(path, _ -> new AtomicInteger()).incrementAndGet();
            handler.handle(exchange);
        });
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private int hitsOf(String path) {
        return hits.getOrDefault(path, new AtomicInteger()).get();
    }

    /** Much longer than any attempt timeout in these tests, so the client always gives up first. */
    private static void stall() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HttpClientProperties.class)
    static class PropertiesConfiguration {}
}

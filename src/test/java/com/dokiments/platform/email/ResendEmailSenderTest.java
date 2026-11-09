package com.dokiments.platform.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.platform.http.HttpClientProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sending through Resend, against a stand-in for Resend's API on this machine. The application's own HTTP client setup
 * is used, so this also covers how Resend's failures are reported.
 */
class ResendEmailSenderTest {

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private int answerStatus = 200;
    private String answerBody = "{\"id\":\"email_123\"}";
    private HttpServer resend;

    @BeforeEach
    void startResend() throws IOException {
        resend = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        resend.createContext("/emails", this::receive);
        resend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        resend.start();
    }

    @AfterEach
    void stopResend() {
        resend.stop(0);
    }

    @Test
    void anEmailIsSentWithBothVersionsAndTheConfiguredSender() {
        context().run(app -> {
            EmailResult result = app.getBean(EmailSender.class).send(signedEmail("owner@example.com"));

            assertThat(result).isEqualTo(new EmailResult.Sent("email_123"));
            assertThat(received).singleElement().satisfies(request -> {
                assertThat(request.authorization()).isEqualTo("Bearer re_test_key");
                assertThat(request.idempotencyKey()).isNull();
                JsonNode body = request.body();
                assertThat(body.get("from").asString()).isEqualTo("Dokiments <notifications@mail.dokiments.com>");
                assertThat(body.get("to").get(0).asString()).isEqualTo("owner@example.com");
                assertThat(body.get("subject").asString()).isEqualTo("Bob Smith signed \"Service agreement\"");
                assertThat(body.get("html").asString()).contains("Bob Smith signed your document");
                assertThat(body.get("text").asString()).startsWith("Bob Smith signed your document");
                assertThat(body.get("reply_to").asString()).isEqualTo("support@dokiments.com");
            });
        });
    }

    @Test
    void theIdempotencyKeyIsPassedOn() {
        context().run(app -> {
            app.getBean(EmailSender.class).send(signedEmail("owner@example.com").withIdempotencyKey("signed-abc"));

            assertThat(received)
                    .singleElement()
                    .extracting(Received::idempotencyKey)
                    .isEqualTo("signed-abc");
        });
    }

    @Test
    void withoutAKeyNothingIsSent() {
        context("dokiments.email.resend-api-key=").run(app -> {
            EmailResult result = app.getBean(EmailSender.class).send(signedEmail("owner@example.com"));

            assertThat(result).isEqualTo(new EmailResult.NotSent(EmailResult.Reason.DISABLED));
            assertThat(received).isEmpty();
        });
    }

    @Test
    void whereRecipientsAreRestrictedOnlyTheAllowListReceivesEmail() {
        context("dokiments.email.restrict-recipients=true", "dokiments.email.allow-list=tester@example.com")
                .run(app -> {
                    var sender = app.getBean(EmailSender.class);

                    assertThat(sender.send(signedEmail("customer@example.com")))
                            .isEqualTo(new EmailResult.NotSent(EmailResult.Reason.BLOCKED));
                    assertThat(sender.send(signedEmail("Tester@Example.com"))).isInstanceOf(EmailResult.Sent.class);
                    assertThat(received).hasSize(1);
                });
    }

    @Test
    void anImplausibleAddressIsNotSent() {
        context().run(app -> {
            EmailResult result = app.getBean(EmailSender.class).send(signedEmail("not an address"));

            assertThat(result).isEqualTo(new EmailResult.NotSent(EmailResult.Reason.INVALID_RECIPIENT));
            assertThat(received).isEmpty();
        });
    }

    @Test
    void anEmailResendRejectsIsReportedNotThrown() {
        answerStatus = 422;
        answerBody = "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"Invalid `to` field.\"}";

        context()
                .run(app -> assertThat(app.getBean(EmailSender.class).send(signedEmail("owner@example.com")))
                        .isEqualTo(new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR)));
    }

    @Test
    void resendBeingDownIsReportedNotThrown() {
        answerStatus = 503;
        answerBody = "{}";

        context()
                .run(app -> assertThat(app.getBean(EmailSender.class).send(signedEmail("owner@example.com")))
                        .isEqualTo(new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR)));
    }

    @Test
    void addressesInTheLogsAreMasked() {
        assertThat(ResendEmailSender.mask("jane@example.com")).isEqualTo("j***@example.com");
        assertThat(ResendEmailSender.mask("no-at-sign")).isEqualTo("***");
    }

    private ApplicationContextRunner context(String... properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class,
                        HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "dokiments.email.resend-api-key=re_test_key",
                        "dokiments.email.reply-to=support@dokiments.com",
                        "dokiments.email.resend-url=http://127.0.0.1:"
                                + resend.getAddress().getPort())
                .withPropertyValues(properties);
    }

    private static EmailMessage signedEmail(String to) {
        return EmailMessage.of(
                to,
                "Bob Smith signed \"Service agreement\"",
                "sharing/document-signed",
                Map.of(
                        "signerName", "Bob Smith",
                        "documentName", "Service agreement",
                        "signedAt", "31 Oct 2026, 21:30 UTC",
                        "url", "https://dokiments.com/my-documents/shared/abc"));
    }

    private void receive(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                JsonMapper.builder().build().readTree(body)));
        byte[] answer = answerBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answerStatus, answer.length);
        try (var out = exchange.getResponseBody()) {
            out.write(answer);
        }
    }

    private record Received(
            @Nullable String authorization, @Nullable String idempotencyKey, JsonNode body) {}

    /** The email setup, plus the application's HTTP client rules, which are package-private, so scanned for. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({EmailProperties.class, HttpClientProperties.class})
    @Import(EmailConfiguration.class)
    @ComponentScan("com.dokiments.platform.http")
    static class TestConfiguration {}
}

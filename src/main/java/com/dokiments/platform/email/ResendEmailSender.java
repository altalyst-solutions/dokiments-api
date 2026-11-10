package com.dokiments.platform.email;

import com.dokiments.shared.error.UpstreamUnavailableException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends email through Resend's HTTP API ({@code POST /emails}), after checking the recipient against the settings.
 *
 * <p>Logs name recipients only partly ({@code j***@example.com}), so the logs never hold full email addresses.
 */
final class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    /** A plausibility check only. Resend does the strict validation. */
    private static final Pattern PLAUSIBLE_ADDRESS = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");

    private final EmailProperties properties;
    private final EmailTemplates templates;
    private final RestClient resend;
    private final MeterRegistry meterRegistry;

    ResendEmailSender(
            EmailProperties properties,
            EmailTemplates templates,
            RestClient.Builder restClientBuilder,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.templates = templates;
        this.meterRegistry = meterRegistry;
        this.resend = restClientBuilder
                .baseUrl(properties.resendUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
                .build();
    }

    @Override
    public EmailResult send(EmailMessage message) {
        EmailResult result = deliver(message);
        Counter.builder("dokiments.emails")
                .description("Emails the application tried to send, by template and what happened")
                .tag("template", message.template())
                .tag("result", resultTag(result))
                .register(meterRegistry)
                .increment();
        return result;
    }

    /**
     * {@code sent}, or why not: {@code disabled}, {@code blocked}, {@code invalid_recipient}, {@code provider_error}.
     */
    static String resultTag(EmailResult result) {
        return switch (result) {
            case EmailResult.Sent _ -> "sent";
            case EmailResult.NotSent notSent -> notSent.reason().name().toLowerCase(Locale.ROOT);
        };
    }

    private EmailResult deliver(EmailMessage message) {
        String recipient = message.to();
        if (!PLAUSIBLE_ADDRESS.matcher(recipient).matches()) {
            log.warn("Email \"{}\" not sent: the recipient isn't a valid address", message.template());
            return new EmailResult.NotSent(EmailResult.Reason.INVALID_RECIPIENT);
        }

        // Rendered even when it won't be sent, so a broken template shows up locally and in tests, not only in
        // production.
        EmailTemplates.Rendered rendered = templates.render(message);

        if (properties.mode() == EmailMode.DISABLED) {
            log.info(
                    "Email \"{}\" to {} not sent: email is off (no RESEND_API_KEY)",
                    message.template(),
                    mask(recipient));
            // Lets a developer read the email locally. Debug level is only on in dev.
            log.debug("Subject: {}\n{}", message.subject(), rendered.text());
            return new EmailResult.NotSent(EmailResult.Reason.DISABLED);
        }
        if (!properties.allows(recipient)) {
            log.info("Email \"{}\" to {} not sent: not on EMAIL_ALLOW_LIST", message.template(), mask(recipient));
            return new EmailResult.NotSent(EmailResult.Reason.BLOCKED);
        }

        try {
            var request = resend.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendEmail(
                            properties.from(),
                            List.of(recipient),
                            message.subject(),
                            rendered.html(),
                            rendered.text(),
                            message.replyTo() != null ? message.replyTo() : properties.replyTo()));
            if (message.idempotencyKey() != null) {
                request = request.header("Idempotency-Key", message.idempotencyKey());
            }
            ResendResponse response = request.retrieve().body(ResendResponse.class);
            if (response == null || response.id() == null) {
                log.error("Resend accepted email \"{}\" to {} without an ID", message.template(), mask(recipient));
                return new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR);
            }
            log.info("Email \"{}\" sent to {} ({})", message.template(), mask(recipient), response.id());
            return new EmailResult.Sent(response.id());
        } catch (RestClientResponseException e) {
            // Only the error's name (for example "validation_error"): its message can repeat an address.
            ResendError error = e.getResponseBodyAs(ResendError.class);
            log.error(
                    "Resend rejected email \"{}\" to {} with {} {}",
                    message.template(),
                    mask(recipient),
                    e.getStatusCode().value(),
                    error != null && error.name() != null ? error.name() : "(no error name)");
            return new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR);
        } catch (UpstreamUnavailableException e) {
            log.warn("Email \"{}\" to {} not sent: Resend couldn't be reached", message.template(), mask(recipient), e);
            return new EmailResult.NotSent(EmailResult.Reason.PROVIDER_ERROR);
        }
    }

    /** {@code jane@example.com} becomes {@code j***@example.com}. */
    static String mask(String address) {
        int at = address.indexOf('@');
        return at <= 0 ? "***" : address.charAt(0) + "***" + address.substring(at);
    }

    /** The request body of Resend's {@code POST /emails}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResendEmail(
            String from,
            List<String> to,
            String subject,
            String html,
            String text,
            @JsonProperty("reply_to") @Nullable String replyTo) {}

    /** The part of Resend's answer that matters here. */
    record ResendResponse(@Nullable String id) {}

    /** Resend's error body, for example {@code {"statusCode": 422, "name": "validation_error", "message": "..."}}. */
    record ResendError(@Nullable String name) {}
}

package com.dokiments.platform.email;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Creates the {@link EmailSender}, and says at start-up who will receive email. */
@Configuration(proxyBeanMethods = false)
class EmailConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmailConfiguration.class);

    @Bean
    EmailSender emailSender(
            EmailProperties properties, RestClient.Builder restClientBuilder, MeterRegistry meterRegistry) {
        switch (properties.mode()) {
            case DISABLED -> log.info("Email is off: no RESEND_API_KEY is set, so nothing will be sent");
            case RESTRICTED ->
                log.info(
                        "Email is restricted: only the {} address(es) on EMAIL_ALLOW_LIST will receive email",
                        properties.allowList().size());
            case LIVE -> log.info("Email is on: sending to every recipient through Resend");
        }
        return new ResendEmailSender(
                properties, new EmailTemplates(properties.siteUrl()), restClientBuilder, meterRegistry);
    }
}

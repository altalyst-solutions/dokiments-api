package com.dokiments.newsletter.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.newsletter.application.port.in.SubscribeToNewsletter.SubscribeCommand;
import com.dokiments.newsletter.application.port.out.NewsletterSubscribers;
import com.dokiments.newsletter.domain.InvalidSubscriberEmailException;
import com.dokiments.newsletter.domain.Subscriber;
import com.dokiments.newsletter.domain.SubscriberEmail;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsletterServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");

    private final List<Subscriber> saved = new ArrayList<>();
    private final NewsletterSubscribers subscribers = saved::add;
    private final NewsletterService service = new NewsletterService(subscribers, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aSubscriberIsStoredWithTheCleanedAddressAndTheTime() {
        service.subscribe(new SubscribeCommand(" Jane@Example.com "));

        assertThat(saved).containsExactly(new Subscriber(new SubscriberEmail("jane@example.com"), NOW));
    }

    @Test
    void nothingIsStoredForAnImplausibleAddress() {
        assertThatThrownBy(() -> service.subscribe(new SubscribeCommand("not an address")))
                .isInstanceOf(InvalidSubscriberEmailException.class);
        assertThat(saved).isEmpty();
    }
}

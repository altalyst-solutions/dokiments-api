package com.dokiments.newsletter.application.service;

import com.dokiments.newsletter.application.port.in.SubscribeToNewsletter;
import com.dokiments.newsletter.application.port.out.NewsletterSubscribers;
import com.dokiments.newsletter.domain.Subscriber;
import com.dokiments.newsletter.domain.SubscriberEmail;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
class NewsletterService implements SubscribeToNewsletter {

    private final NewsletterSubscribers subscribers;
    private final Clock clock;

    NewsletterService(NewsletterSubscribers subscribers, Clock clock) {
        this.subscribers = subscribers;
        this.clock = clock;
    }

    @Override
    public void subscribe(SubscribeCommand command) {
        subscribers.save(new Subscriber(SubscriberEmail.of(command.email()), clock.instant()));
    }
}

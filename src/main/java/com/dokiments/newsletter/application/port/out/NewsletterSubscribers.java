package com.dokiments.newsletter.application.port.out;

import com.dokiments.newsletter.domain.Subscriber;

/** Where subscribers are kept. */
public interface NewsletterSubscribers {

    /** Stores the subscriber, replacing an earlier subscription with the same address. */
    void save(Subscriber subscriber);
}

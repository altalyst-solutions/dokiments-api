package com.dokiments.newsletter.domain;

import com.dokiments.shared.EmailAddresses;
import java.util.Locale;

/**
 * The address someone subscribed with, cleaned up the way the web app does it: surrounding spaces removed and lower
 * case, so subscribing again with {@code " Jane@Example.com "} is the same subscription as {@code jane@example.com}.
 *
 * @param value the cleaned address
 */
public record SubscriberEmail(String value) {

    public SubscriberEmail {
        if (EmailAddresses.isImplausible(value)) {
            throw new InvalidSubscriberEmailException();
        }
    }

    /**
     * Cleans and checks an address as typed.
     *
     * @throws InvalidSubscriberEmailException if it isn't a plausible address, or is longer than 254 characters
     */
    public static SubscriberEmail of(String typed) {
        return new SubscriberEmail(EmailAddresses.trim(typed).toLowerCase(Locale.ROOT));
    }
}

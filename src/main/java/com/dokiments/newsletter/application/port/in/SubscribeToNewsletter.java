package com.dokiments.newsletter.application.port.in;

/** Adds an address to the newsletter list. Subscribing again with the same address keeps a single subscription. */
public interface SubscribeToNewsletter {

    /**
     * Subscribes the address in {@code command}.
     *
     * @throws com.dokiments.newsletter.domain.InvalidSubscriberEmailException if the address isn't plausible
     * @throws com.dokiments.shared.error.UpstreamUnavailableException if the subscription couldn't be stored
     */
    void subscribe(SubscribeCommand command);

    /**
     * A request to subscribe.
     *
     * @param email the address as typed; the use case cleans and checks it
     */
    record SubscribeCommand(String email) {}
}

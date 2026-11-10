package com.dokiments.newsletter.domain;

import java.time.Instant;

/**
 * Someone on the newsletter list.
 *
 * @param email their address
 * @param subscribedAt when they last subscribed. Subscribing again moves it forward, as in the web app.
 */
public record Subscriber(SubscriberEmail email, Instant subscribedAt) {}

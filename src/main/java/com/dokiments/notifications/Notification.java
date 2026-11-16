package com.dokiments.notifications;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Something to tell a user about, such as a template newly added to the catalog. Notifications aren't stored: each
 * {@link NotificationSource} works them out from its own data whenever the list is asked for.
 *
 * @param id stays the same each time the source works it out, so the web app can tell it apart, such as
 *     {@code template:classic-invoice}
 * @param kind what it is about, the source's {@link NotificationSource#kind()}
 * @param title the line shown in bold
 * @param body the line below it, if any
 * @param action a link to follow, if any
 * @param createdAt when it happened, which decides its place in the list and whether it is read
 */
public record Notification(
        String id,
        String kind,
        String title,
        @Nullable String body,
        @Nullable Action action,
        Instant createdAt) {

    /** How far back notifications go. Older ones aren't shown. */
    public static final Duration WINDOW = Duration.ofDays(14);

    /**
     * A link a notification offers.
     *
     * @param label the link's text
     * @param href where it goes in the web app, such as {@code /marketplace}
     */
    public record Action(String label, @Nullable String href) {}
}

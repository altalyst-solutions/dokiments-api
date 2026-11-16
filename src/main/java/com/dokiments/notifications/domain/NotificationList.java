package com.dokiments.notifications.domain;

import com.dokiments.notifications.Notification;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** The web app's rules for the notification list. */
public final class NotificationList {

    private NotificationList() {}

    /**
     * The list: only notifications from the last {@link Notification#WINDOW}, newest first, each read when it happened
     * no later than {@code seenAt}. Times count to the millisecond, as the web app compares them.
     */
    public static List<ShownNotification> of(List<Notification> notifications, Instant seenAt, Instant now) {
        long nowMillis = now.toEpochMilli();
        long seenMillis = seenAt.toEpochMilli();
        long window = Notification.WINDOW.toMillis();
        return notifications.stream()
                .filter(notification -> nowMillis - notification.createdAt().toEpochMilli() <= window)
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .map(notification -> new ShownNotification(
                        notification, notification.createdAt().toEpochMilli() <= seenMillis))
                .toList();
    }
}

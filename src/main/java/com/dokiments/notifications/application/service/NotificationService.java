package com.dokiments.notifications.application.service;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.NotificationSource;
import com.dokiments.notifications.application.port.in.ReadNotifications;
import com.dokiments.notifications.application.port.out.SeenNotifications;
import com.dokiments.notifications.domain.NotificationList;
import com.dokiments.notifications.domain.ShownNotification;
import com.dokiments.shared.SignedInUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
class NotificationService implements ReadNotifications {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<NotificationSource> sources;
    private final SeenNotifications seen;
    private final Clock clock;

    NotificationService(ObjectProvider<NotificationSource> sources, SeenNotifications seen, Clock clock) {
        this.sources = sources;
        this.seen = seen;
        this.clock = clock;
    }

    @Override
    public List<ShownNotification> list(SignedInUser user) {
        var now = now();
        // Read first and not guarded: without it, nothing could be shown as read or unread, so it fails the request.
        var seenAt = seen.seenAt(user.uid());
        List<Notification> all = sources.orderedStream()
                .flatMap(source -> from(source, user, now).stream())
                .toList();
        return NotificationList.of(all, seenAt, now);
    }

    @Override
    public Instant markSeen(SignedInUser user) {
        var now = now();
        seen.markSeen(user.uid(), now);
        return now;
    }

    /** One kind's notifications, or none when it fails, so one kind's outage never hides the others. */
    private static List<Notification> from(NotificationSource source, SignedInUser user, Instant now) {
        try {
            return source.list(user, now);
        } catch (RuntimeException e) {
            log.warn("Notifications of kind {} couldn't be worked out, so they are left out", source.kind(), e);
            return List.of();
        }
    }

    /** Milliseconds, the precision the web app stores and answers with. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

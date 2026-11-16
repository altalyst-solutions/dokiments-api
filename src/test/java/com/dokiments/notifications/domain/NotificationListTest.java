package com.dokiments.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.notifications.Notification;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationListTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void onlyTheLastTwoWeeksAreShownNewestFirst() {
        var old = notification("old", NOW.minus(Duration.ofDays(14)).minusMillis(1));
        var edge = notification("edge", NOW.minus(Duration.ofDays(14)));
        var recent = notification("recent", NOW.minus(Duration.ofHours(1)));

        var shown = NotificationList.of(List.of(edge, old, recent), Instant.EPOCH, NOW);

        assertThat(shown.stream().map(item -> item.notification().id())).containsExactly("recent", "edge");
    }

    @Test
    void aNotificationIsReadWhenItHappenedNoLaterThanTheUserLooked() {
        var seenAt = NOW.minus(Duration.ofHours(2));
        var before = notification("before", seenAt.minusMillis(1));
        var at = notification("at", seenAt);
        var after = notification("after", seenAt.plusMillis(1));

        var shown = NotificationList.of(List.of(before, at, after), seenAt, NOW);

        assertThat(shown).extracting(ShownNotification::read).containsExactly(false, true, true);
    }

    private static Notification notification(String id, Instant createdAt) {
        return new Notification(id, "test", "Title", null, null, createdAt);
    }
}

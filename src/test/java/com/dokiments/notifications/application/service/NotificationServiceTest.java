package com.dokiments.notifications.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.NotificationSource;
import com.dokiments.notifications.application.port.out.SeenNotifications;
import com.dokiments.shared.SignedInUser;
import com.dokiments.support.TestUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@NullMarked
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00.123456Z");
    private static final SignedInUser JANE = TestUser.JANE;

    private final Map<String, Instant> seenAt = new HashMap<>();
    private final SeenNotifications seen = new SeenNotifications() {
        @Override
        public Instant seenAt(String uid) {
            return seenAt.getOrDefault(uid, Instant.EPOCH);
        }

        @Override
        public void markSeen(String uid, Instant at) {
            seenAt.put(uid, at);
        }
    };

    @Test
    void everySourceIsAskedAndOneThatFailsIsLeftOut() {
        var service = service(
                source("template", List.of(notification("t", NOW.minusSeconds(60)))),
                failing(),
                source("document-signed", List.of(notification("s", NOW.minusSeconds(30)))));

        assertThat(service.list(JANE).stream().map(item -> item.notification().id()))
                .containsExactly("s", "t");
    }

    @Test
    void markingSeenCountsFromNowToTheMillisecond() {
        var service = service(source("template", List.of(notification("t", NOW.minusSeconds(60)))));

        var marked = service.markSeen(JANE);

        assertThat(marked).isEqualTo(Instant.parse("2026-11-15T12:00:00.123Z"));
        assertThat(seenAt).containsEntry("uid-1", marked);
        assertThat(service.list(JANE).getFirst().read()).isTrue();
    }

    private NotificationService service(NotificationSource... sources) {
        var beans = new StaticListableBeanFactory();
        for (int i = 0; i < sources.length; i++) {
            beans.addBean("source" + i, sources[i]);
        }
        return new NotificationService(
                beans.getBeanProvider(NotificationSource.class), seen, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NotificationSource source(String kind, List<Notification> notifications) {
        return new NotificationSource() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public List<Notification> list(SignedInUser user, Instant now) {
                return notifications;
            }
        };
    }

    /** A kind whose data can't be read. */
    private static NotificationSource failing() {
        return new NotificationSource() {
            @Override
            public String kind() {
                return "document-shared";
            }

            @Override
            public List<Notification> list(SignedInUser user, Instant now) {
                throw new IllegalStateException("Firestore is down");
            }
        };
    }

    private static Notification notification(String id, Instant createdAt) {
        return new Notification(id, "test", "Title", null, null, createdAt);
    }
}

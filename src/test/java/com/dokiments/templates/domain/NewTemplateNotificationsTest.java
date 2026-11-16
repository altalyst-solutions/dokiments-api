package com.dokiments.templates.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.notifications.Notification;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NewTemplateNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final TemplateStyle CLASSIC = new TemplateStyle("classic", "Classic designs", "", TemplateTier.FREE);

    @Test
    void aSingleNewTemplateIsNamedWithItsStyleAndTier() {
        var added = NOW.minus(Duration.ofDays(1));

        var notifications = NewTemplateNotifications.of(List.of(template("classic-invoice", "Invoice", added)), NOW);

        assertThat(notifications)
                .containsExactly(new Notification(
                        "template:classic-invoice",
                        "template",
                        "New template: Invoice",
                        "Classic designs style · free plan",
                        new Notification.Action("View in marketplace", "/marketplace"),
                        added));
    }

    @Test
    void templatesAddedWithinTenMinutesOfEachOtherMakeOneNotification() {
        var start = NOW.minus(Duration.ofHours(5));
        var templates = List.of(
                template("d", "Fourth", start.plus(Duration.ofMinutes(30))),
                template("a", "First", start),
                template("b", "Second", start.plus(Duration.ofMinutes(10))),
                template("c", "Third", start.plus(Duration.ofMinutes(20))),
                template("e", "Later", start.plus(Duration.ofMinutes(41))));

        var notifications = NewTemplateNotifications.of(templates, NOW);

        assertThat(notifications).hasSize(2);
        var batch = notifications.getFirst();
        assertThat(batch.id()).isEqualTo("template:a");
        assertThat(batch.title()).isEqualTo("4 new templates added");
        assertThat(batch.body()).isEqualTo("First, Second, Third, and more");
        assertThat(batch.createdAt()).isEqualTo(start.plus(Duration.ofMinutes(30)));
        assertThat(notifications.get(1).title()).isEqualTo("New template: Later");
    }

    @Test
    void twoOrThreeTemplatesAreAllNamed() {
        var start = NOW.minus(Duration.ofHours(1));

        var notifications = NewTemplateNotifications.of(
                List.of(template("a", "First", start), template("b", "Second", start.plusSeconds(1))), NOW);

        assertThat(notifications.getFirst().body()).isEqualTo("First, Second");
    }

    @Test
    void templatesAddedLongAgoOrWithoutATimeAreLeftOut() {
        var notifications = NewTemplateNotifications.of(
                List.of(template("old", "Old", NOW.minus(Duration.ofDays(15))), template("seeded", "Seeded", null)),
                NOW);

        assertThat(notifications).isEmpty();
    }

    private static Template template(String id, String name, @Nullable Instant createdAt) {
        return new Template(id, name, "", "invoice", List.of(), CLASSIC, TemplateTier.FREE, createdAt);
    }
}

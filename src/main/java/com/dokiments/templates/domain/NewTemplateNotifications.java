package com.dokiments.templates.domain;

import com.dokiments.notifications.Notification;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The web app's notifications about templates newly added to the catalog. Templates added within ten minutes of each
 * other, as when the catalog is seeded, make one notification rather than one each.
 */
public final class NewTemplateNotifications {

    /** The kind of these notifications. */
    public static final String KIND = "template";

    /** Templates added at most this far apart count as one addition. */
    static final Duration BATCH_GAP = Duration.ofMinutes(10);

    private static final Notification.Action VIEW = new Notification.Action("View in marketplace", "/marketplace");

    private NewTemplateNotifications() {}

    /**
     * One notification per addition of the last {@link Notification#WINDOW}: for a single template, its name, style and
     * tier; for several, how many, and the first three names. Templates without a time of adding are left out, since
     * they were in the catalog before that was recorded.
     */
    public static List<Notification> of(List<Template> templates, Instant now) {
        long nowMillis = now.toEpochMilli();
        long window = Notification.WINDOW.toMillis();
        List<Template> added = templates.stream()
                .filter(template -> template.createdAt() != null)
                .filter(template -> nowMillis - millis(template) <= window)
                .sorted(Comparator.comparingLong(NewTemplateNotifications::millis))
                .toList();

        List<List<Template>> batches = new ArrayList<>();
        for (Template template : added) {
            List<Template> current = batches.isEmpty() ? null : batches.getLast();
            if (current != null && millis(template) - millis(current.getLast()) <= BATCH_GAP.toMillis()) {
                current.add(template);
            } else {
                batches.add(new ArrayList<>(List.of(template)));
            }
        }
        return batches.stream().map(NewTemplateNotifications::notification).toList();
    }

    private static Notification notification(List<Template> batch) {
        Template first = batch.getFirst();
        Template newest = batch.getLast();
        boolean single = batch.size() == 1;
        String title = single ? "New template: " + first.name() : batch.size() + " new templates added";
        String body = single
                ? first.style().name() + " style · " + first.tier().value() + " plan"
                : batch.stream().limit(3).map(Template::name).collect(Collectors.joining(", "))
                        + (batch.size() > 3 ? ", and more" : "");
        return new Notification(
                "template:" + first.id(), KIND, title, body, VIEW, Instant.ofEpochMilli(millis(newest)));
    }

    /** The time it was added, to the millisecond as the web app counts it. Only called for templates that have one. */
    private static long millis(Template template) {
        var createdAt = template.createdAt();
        return createdAt == null ? 0 : createdAt.toEpochMilli();
    }
}

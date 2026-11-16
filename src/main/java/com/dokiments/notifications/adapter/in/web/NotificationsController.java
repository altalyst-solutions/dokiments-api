package com.dokiments.notifications.adapter.in.web;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.application.port.in.ReadNotifications;
import com.dokiments.notifications.domain.ShownNotification;
import com.dokiments.shared.SignedInUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's notifications. Paths, bodies, status codes and messages are the web app's. */
@RestController
@Tag(name = "Notifications")
class NotificationsController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ReadNotifications notifications;

    NotificationsController(ReadNotifications notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/notifications")
    @Operation(
            operationId = "listNotifications",
            summary = "List the notifications",
            description =
                    "Those of the last 14 days, newest first, worked out when asked for rather than stored. Each is"
                            + " read when it happened no later than the last call to /api/notifications/read. A kind that can't"
                            + " be worked out is left out rather than failing the list.")
    @ApiResponse(responseCode = "200", description = "The notifications, an empty list when there are none")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    NotificationsResponse list(@AuthenticationPrincipal SignedInUser user) {
        return new NotificationsResponse(
                notifications.list(user).stream().map(NotificationResponse::of).toList());
    }

    @PostMapping("/api/notifications/read")
    @Operation(
            operationId = "markNotificationsRead",
            summary = "Mark the notifications read",
            description = "Every notification so far counts as read from now on.")
    @ApiResponse(responseCode = "200", description = "Marked; lastSeenAt is the time it counts from, in milliseconds")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    SeenResponse markRead(@AuthenticationPrincipal SignedInUser user) {
        return new SeenResponse(notifications.markSeen(user).toEpochMilli());
    }

    /** The answer to listing: {@code { "notifications": [...] }}. */
    record NotificationsResponse(List<NotificationResponse> notifications) {}

    /**
     * A notification.
     *
     * @param action a link to follow; left out when there is none
     * @param body the line below the title; left out when there is none
     * @param createdAt when it happened, in milliseconds since 1970
     * @param read whether it happened no later than the user last marked the list read
     */
    record NotificationResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable ActionResponse action,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String body,

            long createdAt,
            @Schema(example = "template:classic-invoice") String id,
            @Schema(example = "template") String kind,
            boolean read,
            String title) {

        static NotificationResponse of(ShownNotification shown) {
            Notification notification = shown.notification();
            var action = notification.action();
            return new NotificationResponse(
                    action == null ? null : new ActionResponse(action.href(), action.label()),
                    notification.body(),
                    notification.createdAt().toEpochMilli(),
                    notification.id(),
                    notification.kind(),
                    shown.read(),
                    notification.title());
        }
    }

    /** A notification's link. */
    record ActionResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(example = "/marketplace") @Nullable String href,

            String label) {}

    /**
     * The answer to marking read.
     *
     * @param lastSeenAt the time the notifications count as read from, in milliseconds since 1970
     */
    record SeenResponse(long lastSeenAt) {}
}

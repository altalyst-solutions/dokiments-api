package com.dokiments.notifications.domain;

import com.dokiments.notifications.Notification;

/**
 * A notification as the list shows it.
 *
 * @param notification the notification
 * @param read whether it happened no later than the user last looked at the list
 */
public record ShownNotification(Notification notification, boolean read) {}

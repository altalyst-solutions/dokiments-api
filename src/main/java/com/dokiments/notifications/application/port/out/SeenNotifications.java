package com.dokiments.notifications.application.port.out;

import java.time.Instant;

/** When each user last looked at their notifications. */
public interface SeenNotifications {

    /** When the user last looked, or the start of 1970 if never. */
    Instant seenAt(String uid);

    void markSeen(String uid, Instant seenAt);
}

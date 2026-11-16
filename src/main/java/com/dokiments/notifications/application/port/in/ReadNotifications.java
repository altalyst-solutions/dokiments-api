package com.dokiments.notifications.application.port.in;

import com.dokiments.notifications.domain.ShownNotification;
import com.dokiments.shared.SignedInUser;
import java.time.Instant;
import java.util.List;

/** The signed-in user's notifications. */
public interface ReadNotifications {

    /** The notifications of the last two weeks, newest first, each marked read or not. */
    List<ShownNotification> list(SignedInUser user);

    /** Marks every notification so far as read, and returns the time it counts from. */
    Instant markSeen(SignedInUser user);
}

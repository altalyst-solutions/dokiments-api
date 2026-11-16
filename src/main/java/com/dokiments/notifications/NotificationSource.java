package com.dokiments.notifications;

import com.dokiments.shared.SignedInUser;
import java.time.Instant;
import java.util.List;

/**
 * One kind of notification, worked out from the data of the module that offers it. A module adds a kind by declaring a
 * Spring bean implementing this; the notifications module asks every one of them for the list.
 */
public interface NotificationSource {

    /** What the notifications are about, such as {@code template}. */
    String kind();

    /**
     * The notifications for the user as of {@code now}. Ones older than {@link Notification#WINDOW} may be left out
     * here, and are left out of the list anyway.
     *
     * <p>A source that throws is left out of the list, rather than failing it, so one kind's outage never hides the
     * others.
     */
    // A kind's notifications may depend on who asks, such as documents shared with them. The new-templates kind
    // doesn't, since the catalog is the same for everyone, so IntelliJ reports the parameter as unused.
    @SuppressWarnings("unused")
    List<Notification> list(SignedInUser user, Instant now);
}

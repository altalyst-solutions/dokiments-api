package com.dokiments.sharing.application.service;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.NotificationSource;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.ShareNotifications;
import com.dokiments.sharing.domain.Viewer;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Notifications about documents others shared with the user. Silent while sharing is off, as its routes are. Someone
 * whose address isn't verified has only the copies their account was linked to, as in the web app.
 */
@Component
class SharedWithYouSource implements NotificationSource {

    private final SharedDocumentStore store;
    private final boolean enabled;

    SharedWithYouSource(SharedDocumentStore store, DokimentsProperties properties) {
        this.store = store;
        this.enabled = properties.features().documentSharing();
    }

    @Override
    public String kind() {
        return ShareNotifications.SHARED_KIND;
    }

    @Override
    public List<Notification> list(SignedInUser user, Instant now) {
        if (!enabled) {
            return List.of();
        }
        var viewer = Viewer.of(user);
        return ShareNotifications.shared(store.sharedWith(viewer), viewer.email());
    }
}

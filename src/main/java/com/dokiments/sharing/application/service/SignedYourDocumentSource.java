package com.dokiments.sharing.application.service;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.NotificationSource;
import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.ShareNotifications;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Notifications about signatures on the documents the user shared. Silent while sharing is off, as its routes are. */
@Component
class SignedYourDocumentSource implements NotificationSource {

    private final SharedDocumentStore store;
    private final boolean enabled;

    SignedYourDocumentSource(SharedDocumentStore store, DokimentsProperties properties) {
        this.store = store;
        this.enabled = properties.features().documentSharing();
    }

    @Override
    public String kind() {
        return ShareNotifications.SIGNED_KIND;
    }

    @Override
    public List<Notification> list(SignedInUser user, Instant now) {
        if (!enabled) {
            return List.of();
        }
        return ShareNotifications.signed(store.ownedBy(user.uid()), user.uid());
    }
}

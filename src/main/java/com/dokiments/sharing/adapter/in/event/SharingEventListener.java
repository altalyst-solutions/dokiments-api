package com.dokiments.sharing.adapter.in.event;

import com.dokiments.sharing.application.port.in.NotifyOwners;
import com.dokiments.sharing.domain.DocumentSigned;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Reacts to what happened to a shared copy. Each listener runs on its own thread, so the request that caused the event
 * answers without waiting for what follows, and what follows can't fail it.
 */
@Component
class SharingEventListener {

    private final NotifyOwners owners;

    SharingEventListener(NotifyOwners owners) {
        this.owners = owners;
    }

    @Async
    @EventListener
    void on(DocumentSigned event) {
        owners.documentSigned(event);
    }
}

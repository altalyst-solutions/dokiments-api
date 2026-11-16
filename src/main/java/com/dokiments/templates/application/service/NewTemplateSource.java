package com.dokiments.templates.application.service;

import com.dokiments.notifications.Notification;
import com.dokiments.notifications.NotificationSource;
import com.dokiments.shared.SignedInUser;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.NewTemplateNotifications;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Notifications about templates newly added to the catalog, the same for every user. */
@Component
class NewTemplateSource implements NotificationSource {

    private final TemplateCatalog catalog;

    NewTemplateSource(TemplateCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String kind() {
        return NewTemplateNotifications.KIND;
    }

    @Override
    public List<Notification> list(SignedInUser user, Instant now) {
        return NewTemplateNotifications.of(catalog.current().templates(), now);
    }
}

package com.dokiments.templates.application.service;

import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.DocumentIds;
import com.dokiments.templates.application.port.in.ManageSavedTemplates;
import com.dokiments.templates.application.port.out.SavedTemplateLibrary;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.Plans;
import com.dokiments.templates.domain.SavedTemplate;
import com.dokiments.templates.domain.SavedTemplateLimitException;
import com.dokiments.templates.domain.TemplateNotFoundException;
import com.dokiments.templates.domain.TemplateNotInPlanException;
import com.dokiments.users.UserProfiles;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class SavedTemplateService implements ManageSavedTemplates {

    private final SavedTemplateLibrary library;
    private final TemplateCatalog catalog;
    private final UserProfiles profiles;
    private final boolean tierLocks;
    private final Clock clock;

    SavedTemplateService(
            SavedTemplateLibrary library,
            TemplateCatalog catalog,
            UserProfiles profiles,
            DokimentsProperties properties,
            Clock clock) {
        this.library = library;
        this.catalog = catalog;
        this.profiles = profiles;
        this.tierLocks = properties.features().tierLocks();
        this.clock = clock;
    }

    @Override
    public List<Entry> list(String uid) {
        var current = catalog.current();
        return library.all(uid).stream()
                .<Entry>mapMulti((saved, keep) ->
                        current.find(saved.templateId()).ifPresent(template -> keep.accept(new Entry(saved, template))))
                .toList();
    }

    @Override
    public Saved save(String uid, String templateId) {
        var template = catalog.current().find(templateId).orElseThrow(TemplateNotFoundException::new);
        // The role as stored now, so an upgrade counts straight away instead of at the next sign-in.
        var role = profiles.roleOf(uid);
        if (!Plans.includes(role, template.tier(), tierLocks)) {
            throw new TemplateNotInPlanException();
        }
        // Milliseconds, the precision the web app stores and answers with.
        var saved = new SavedTemplate(templateId, clock.instant().truncatedTo(ChronoUnit.MILLIS));
        return switch (library.save(uid, saved, Plans.savedTemplateLimit(role))) {
            case ADDED -> new Saved(saved, true);
            case SAVED_AGAIN -> new Saved(saved, false);
            case LIMIT_REACHED -> throw new SavedTemplateLimitException();
        };
    }

    @Override
    public void remove(String uid, String templateId) {
        // An ID no document could have can't be saved, so there is nothing to remove.
        if (DocumentIds.isStorable(templateId)) {
            library.remove(uid, templateId);
        }
    }
}

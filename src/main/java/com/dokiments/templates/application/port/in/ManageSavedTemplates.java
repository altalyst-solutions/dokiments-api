package com.dokiments.templates.application.port.in;

import com.dokiments.templates.domain.SavedTemplate;
import com.dokiments.templates.domain.Template;
import java.util.List;

/** A user's library of saved templates, which documents are made from. Only ever the user {@code uid}'s own. */
public interface ManageSavedTemplates {

    /**
     * The saved templates, each with its template from the catalog. A saved template whose template is no longer in the
     * catalog is left out, rather than shown broken.
     */
    List<Entry> list(String uid);

    /**
     * Saves a template to the library. Saving one that is saved already only updates the time.
     *
     * @throws com.dokiments.templates.domain.TemplateNotFoundException if it isn't in the catalog
     * @throws com.dokiments.templates.domain.TemplateNotInPlanException if the user's plan doesn't include it
     * @throws com.dokiments.templates.domain.SavedTemplateLimitException if a free account already keeps as many as it
     *     may
     */
    Saved save(String uid, String templateId);

    /** Removes a template from the library. Removing one that isn't saved is not an error. */
    void remove(String uid, String templateId);

    /**
     * A saved template with its template.
     *
     * @param saved the saved template
     * @param template its template, from the catalog
     */
    record Entry(SavedTemplate saved, Template template) {}

    /**
     * The result of saving.
     *
     * @param saved the saved template, with the time of this save
     * @param added whether it is new in the library, rather than saved again
     */
    record Saved(SavedTemplate saved, boolean added) {}
}

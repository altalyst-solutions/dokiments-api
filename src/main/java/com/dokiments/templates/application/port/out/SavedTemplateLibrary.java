package com.dokiments.templates.application.port.out;

import com.dokiments.templates.domain.SavedTemplate;
import java.util.List;

/** Where each user's saved templates are kept. Every method works only on the library of the user {@code uid}. */
public interface SavedTemplateLibrary {

    /** All of the user's saved templates, in the order they are stored, by template ID. */
    List<SavedTemplate> all(String uid);

    /**
     * Stores a save, as one step with checking the limit, so two saves at the same moment can't both pass it: a
     * template that isn't saved yet is only added while the library holds fewer than {@code limit}.
     */
    SaveOutcome save(String uid, SavedTemplate saved, int limit);

    /** Removes the saved template, if there is one. */
    void remove(String uid, String templateId);

    /** What saving did. */
    enum SaveOutcome {
        /** The template is new in the library. */
        ADDED,
        /** The template was saved already; only its time changed. */
        SAVED_AGAIN,
        /** Nothing was stored: the library is full. */
        LIMIT_REACHED
    }
}

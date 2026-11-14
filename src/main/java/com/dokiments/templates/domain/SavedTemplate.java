package com.dokiments.templates.domain;

import java.time.Instant;

/**
 * A template a user keeps in their library, to make documents from. It holds only the reference: the template itself
 * comes from the catalog.
 *
 * @param templateId the template's ID, also the saved template's document ID, so a template is saved at most once
 * @param savedAt when it was last saved
 */
public record SavedTemplate(String templateId, Instant savedAt) {}

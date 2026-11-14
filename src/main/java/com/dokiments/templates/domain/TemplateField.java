package com.dokiments.templates.domain;

import org.jspecify.annotations.Nullable;

/**
 * One field of a template's form, as the document editor shows it.
 *
 * @param key where the value is kept in a document
 * @param label the field's label
 * @param placeholder the hint shown in the empty field, if any
 * @param type how the field is edited, such as {@code text}, {@code textarea}, {@code date} or {@code number}
 */
public record TemplateField(
        String key, String label, @Nullable String placeholder, String type) {}

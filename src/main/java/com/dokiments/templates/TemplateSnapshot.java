package com.dokiments.templates;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A template's content, for a document to keep a copy of: what the document looks like and which form it has. A
 * document made from a template keeps this copy as it was then, so later changes to the catalog, or a template being
 * hidden, never change or break a document already made.
 *
 * @param name the template's name
 * @param description what the template is for
 * @param documentType the kind of document, such as {@code invoice}
 * @param fields the form
 * @param style the style it is in
 */
public record TemplateSnapshot(String name, String description, String documentType, List<Field> fields, Style style) {

    public TemplateSnapshot {
        fields = List.copyOf(fields);
    }

    /**
     * One field of the form.
     *
     * @param key where the value is kept in the document
     * @param label the field's label
     * @param placeholder the hint shown in the empty field, if any
     * @param type how the field is edited, such as {@code text} or {@code date}
     */
    public record Field(String key, String label, @Nullable String placeholder, String type) {}

    /**
     * The style.
     *
     * @param id the style's ID, such as {@code classic}
     * @param name its name
     * @param description what it looks like
     * @param tier which plans include it, such as {@code free}
     */
    public record Style(String id, String name, String description, String tier) {}
}

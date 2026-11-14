package com.dokiments.templates.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A template in the catalog: a kind of document, such as an invoice, in one style.
 *
 * @param id the template's ID, also its document ID, such as {@code classic-invoice}
 * @param name the name shown to users
 * @param description what the template is for
 * @param documentType the kind of document, such as {@code invoice}
 * @param fields the form a document made from it has
 * @param style its style
 * @param tier which plans include it
 * @param createdAt when it was added to the catalog, unknown for templates added before that was recorded
 */
public record Template(
        String id,
        String name,
        String description,
        String documentType,
        List<TemplateField> fields,
        TemplateStyle style,
        TemplateTier tier,
        @Nullable Instant createdAt) {

    public Template {
        fields = List.copyOf(fields);
    }
}

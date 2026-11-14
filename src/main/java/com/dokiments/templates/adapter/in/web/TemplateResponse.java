package com.dokiments.templates.adapter.in.web;

import com.dokiments.templates.domain.Template;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A template, with its style.
 *
 * @param createdAt when it was added to the catalog, in milliseconds since 1970; left out for templates added before
 *     that was recorded
 */
record TemplateResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long createdAt,
        String description,
        @Schema(example = "invoice") String documentType,
        List<FieldResponse> fields,
        @Schema(example = "classic-invoice") String id,
        String name,
        StyleResponse style,

        @Schema(allowableValues = {"free", "silver", "gold"})
        String tier) {

    static TemplateResponse of(Template template) {
        var createdAt = template.createdAt();
        return new TemplateResponse(
                createdAt == null ? null : createdAt.toEpochMilli(),
                template.description(),
                template.documentType(),
                template.fields().stream().map(FieldResponse::of).toList(),
                template.id(),
                template.name(),
                StyleResponse.of(template.style()),
                template.tier().value());
    }
}

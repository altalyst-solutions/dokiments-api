package com.dokiments.templates.adapter.in.web;

import com.dokiments.templates.domain.TemplateField;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * A field of a template's form.
 *
 * @param placeholder left out when the template has none
 */
record FieldResponse(
        String key,
        String label,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String placeholder,
        @Schema(example = "text") String type) {

    static FieldResponse of(TemplateField field) {
        return new FieldResponse(field.key(), field.label(), field.placeholder(), field.type());
    }
}

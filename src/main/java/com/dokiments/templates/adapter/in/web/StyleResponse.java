package com.dokiments.templates.adapter.in.web;

import com.dokiments.templates.domain.TemplateStyle;
import io.swagger.v3.oas.annotations.media.Schema;

/** A style. */
record StyleResponse(
        String description,
        @Schema(example = "classic") String id,
        String name,

        @Schema(allowableValues = {"free", "silver", "gold"})
        String tier) {

    static StyleResponse of(TemplateStyle style) {
        return new StyleResponse(
                style.description(), style.id(), style.name(), style.tier().value());
    }
}

package com.dokiments.templates.domain;

import java.util.Set;

/**
 * A visual style that templates come in, such as {@code classic}. A template's tier is the tier of its style.
 *
 * @param id the style's ID, also its document ID
 * @param name the name shown to users
 * @param description what the style looks like
 * @param tier which plans include it
 */
public record TemplateStyle(String id, String name, String description, TemplateTier tier) {

    /** The styles the web app knows, and so the only ones the catalog can be filtered by. */
    public static final Set<String> KNOWN_IDS = Set.of("classic", "modern", "brutalist", "minimalist");
}

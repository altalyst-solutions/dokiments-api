package com.dokiments.templates.domain;

import com.dokiments.shared.error.InvalidRequestException;
import org.jspecify.annotations.Nullable;

/**
 * Which templates to list: those of one style, of one tier, or both. Either may be left out.
 *
 * @param styleId only templates in this style, or all when {@code null}
 * @param tier only templates of this tier, or all when {@code null}
 */
public record CatalogFilter(
        @Nullable String styleId, @Nullable TemplateTier tier) {

    /** The web app's wording, the same as {@code platform.web.ErrorMessages.INVALID_QUERY}. */
    static final String INVALID_QUERY = "Invalid query parameters.";

    /**
     * The filter from the query, checked as the web app checks it: the style must be a style the web app knows, and the
     * tier must be named exactly.
     *
     * @throws InvalidRequestException if either names something that doesn't exist
     */
    public static CatalogFilter of(@Nullable String styleId, @Nullable String tier) {
        if (styleId != null && !TemplateStyle.KNOWN_IDS.contains(styleId)) {
            throw new InvalidRequestException(INVALID_QUERY);
        }
        TemplateTier parsedTier = null;
        if (tier != null) {
            parsedTier = TemplateTier.parse(tier).orElseThrow(() -> new InvalidRequestException(INVALID_QUERY));
        }
        return new CatalogFilter(styleId, parsedTier);
    }

    boolean matches(Template template) {
        return (styleId == null || template.style().id().equals(styleId)) && (tier == null || template.tier() == tier);
    }
}

package com.dokiments.templates.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.templates.domain.Catalog.StoredStyle;
import com.dokiments.templates.domain.Catalog.StoredTemplate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CatalogTest {

    private static final TemplateStyle CLASSIC = new TemplateStyle("classic", "Classic", "", TemplateTier.FREE);
    private static final TemplateStyle MODERN = new TemplateStyle("modern", "Modern", "", TemplateTier.SILVER);
    private static final TemplateStyle HIDDEN = new TemplateStyle("minimalist", "Minimalist", "", TemplateTier.FREE);

    private final Catalog catalog = Catalog.of(
            List.of(
                    new StoredStyle(MODERN, true, 2L),
                    new StoredStyle(HIDDEN, false, 0L),
                    new StoredStyle(CLASSIC, true, 1L)),
            List.of(
                    stored("unordered", "classic", true, null),
                    stored("modern-invoice", "modern", true, 3L),
                    stored("classic-invoice", "classic", true, 2L),
                    stored("classic-contract", "classic", true, 2L),
                    stored("hidden", "classic", false, 0L),
                    stored("in-hidden-style", "minimalist", true, 0L),
                    stored("in-missing-style", "gone", true, 0L)));

    @Test
    void onlyActiveStylesAreShownInTheirOrder() {
        assertThat(catalog.styles()).containsExactly(CLASSIC, MODERN);
    }

    @Test
    void onlyActiveTemplatesOfActiveStylesAreShownInTheirOrderWithoutAnOrderLast() {
        assertThat(catalog.templates().stream().map(Template::id))
                .containsExactly("classic-invoice", "classic-contract", "modern-invoice", "unordered");
        assertThat(catalog.find("classic-invoice").orElseThrow().style()).isEqualTo(CLASSIC);
        assertThat(catalog.find("hidden")).isEmpty();
        assertThat(catalog.find("in-hidden-style")).isEmpty();
        assertThat(catalog.find("in-missing-style")).isEmpty();
    }

    @Test
    void theTemplatesCanBeFilteredByStyleAndTier() {
        assertThat(catalog.templates(CatalogFilter.of("classic", null)).stream().map(Template::id))
                .containsExactly("classic-invoice", "classic-contract", "unordered");
        assertThat(catalog.templates(CatalogFilter.of(null, "silver")).stream().map(Template::id))
                .containsExactly("modern-invoice");
        assertThat(catalog.templates(CatalogFilter.of("modern", "free"))).isEmpty();
        assertThat(catalog.templates(CatalogFilter.of(null, null))).hasSize(4);
    }

    private static StoredTemplate stored(String id, String styleId, boolean active, @Nullable Long sortOrder) {
        var tier = styleId.equals("modern") ? TemplateTier.SILVER : TemplateTier.FREE;
        return new StoredTemplate(id, id, "", "invoice", List.of(), styleId, tier, null, active, sortOrder);
    }
}

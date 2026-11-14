package com.dokiments.templates.application.port.in;

import com.dokiments.templates.domain.CatalogFilter;
import com.dokiments.templates.domain.Template;
import com.dokiments.templates.domain.TemplateStyle;
import java.util.List;

/** The template catalog, the same for every signed-in user. */
public interface BrowseTemplates {

    /** Every active style, and the active templates matching the filter, each in their intended order. */
    Listing list(CatalogFilter filter);

    /**
     * One template of the catalog.
     *
     * @throws com.dokiments.templates.domain.TemplateNotFoundException if it isn't in the catalog, or is hidden
     */
    Template get(String templateId);

    /**
     * What the catalog page shows.
     *
     * @param styles every active style, never filtered
     * @param templates the templates matching the filter
     */
    record Listing(List<TemplateStyle> styles, List<Template> templates) {}
}

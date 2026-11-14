package com.dokiments.templates.application.service;

import com.dokiments.templates.application.port.in.BrowseTemplates;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.CatalogFilter;
import com.dokiments.templates.domain.Template;
import com.dokiments.templates.domain.TemplateNotFoundException;
import org.springframework.stereotype.Service;

@Service
class CatalogService implements BrowseTemplates {

    private final TemplateCatalog catalog;

    CatalogService(TemplateCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Listing list(CatalogFilter filter) {
        var current = catalog.current();
        return new Listing(current.styles(), current.templates(filter));
    }

    @Override
    public Template get(String templateId) {
        return catalog.current().find(templateId).orElseThrow(TemplateNotFoundException::new);
    }
}

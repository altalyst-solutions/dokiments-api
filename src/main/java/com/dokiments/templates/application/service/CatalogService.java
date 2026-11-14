package com.dokiments.templates.application.service;

import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.templates.Templates;
import com.dokiments.templates.application.port.in.BrowseTemplates;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.CatalogFilter;
import com.dokiments.templates.domain.Template;
import com.dokiments.templates.domain.TemplateNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class CatalogService implements BrowseTemplates, Templates {

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

    @Override
    public Optional<TemplateSnapshot> snapshot(String templateId) {
        return catalog.current().find(templateId).map(CatalogService::snapshotOf);
    }

    private static TemplateSnapshot snapshotOf(Template template) {
        var style = template.style();
        return new TemplateSnapshot(
                template.name(),
                template.description(),
                template.documentType(),
                template.fields().stream()
                        .map(field -> new TemplateSnapshot.Field(
                                field.key(), field.label(), field.placeholder(), field.type()))
                        .toList(),
                new TemplateSnapshot.Style(
                        style.id(),
                        style.name(),
                        style.description(),
                        style.tier().value()));
    }
}

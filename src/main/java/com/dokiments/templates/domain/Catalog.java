package com.dokiments.templates.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The template catalog: the active styles and the active templates, each in its intended order.
 *
 * @param styles the active styles, in their order
 * @param templates the active templates whose style is active too, in their order
 */
public record Catalog(List<TemplateStyle> styles, List<Template> templates) {

    public Catalog {
        styles = List.copyOf(styles);
        templates = List.copyOf(templates);
    }

    /**
     * A style as stored, with what decides whether and where it is shown.
     *
     * @param style the style
     * @param active whether it is shown; a style can be hidden without deleting it
     * @param sortOrder where it goes, lowest first; without one, it goes last
     */
    public record StoredStyle(
            TemplateStyle style, boolean active, @Nullable Long sortOrder) {}

    /**
     * A template as stored, naming its style by ID.
     *
     * @param id the template's ID
     * @param name the name shown to users
     * @param description what the template is for
     * @param documentType the kind of document
     * @param fields the form a document made from it has
     * @param styleId the ID of its style
     * @param tier which plans include it
     * @param createdAt when it was added, if recorded
     * @param active whether it is shown
     * @param sortOrder where it goes, lowest first; without one, it goes last
     */
    public record StoredTemplate(
            String id,
            String name,
            String description,
            String documentType,
            List<TemplateField> fields,
            String styleId,
            TemplateTier tier,
            @Nullable Instant createdAt,
            boolean active,
            @Nullable Long sortOrder) {}

    /**
     * The catalog as the web app builds it from what is stored: only active styles and templates, each sorted by its
     * order, and a template only when its style is active. Entries with the same order keep the order they are given
     * in, which is the order Firestore lists them, by ID.
     */
    public static Catalog of(List<StoredStyle> storedStyles, List<StoredTemplate> storedTemplates) {
        var styles = storedStyles.stream()
                .filter(StoredStyle::active)
                .sorted(Comparator.comparingLong(stored -> order(stored.sortOrder())))
                .map(StoredStyle::style)
                .toList();
        Map<String, TemplateStyle> stylesById = styles.stream()
                .collect(Collectors.toMap(TemplateStyle::id, Function.identity(), (first, next) -> first));
        var templates = storedTemplates.stream()
                .filter(StoredTemplate::active)
                .sorted(Comparator.comparingLong(stored -> order(stored.sortOrder())))
                .<Template>mapMulti((stored, keep) -> {
                    // A template whose style is missing or hidden isn't shown either.
                    TemplateStyle style = stylesById.get(stored.styleId());
                    if (style != null) {
                        keep.accept(new Template(
                                stored.id(),
                                stored.name(),
                                stored.description(),
                                stored.documentType(),
                                stored.fields(),
                                style,
                                stored.tier(),
                                stored.createdAt()));
                    }
                })
                .toList();
        return new Catalog(styles, templates);
    }

    /** The template with this ID, if it is in the catalog. */
    public Optional<Template> find(String templateId) {
        return templates.stream()
                .filter(template -> template.id().equals(templateId))
                .findFirst();
    }

    /** The templates matching a filter. The styles are never filtered: the web app shows all of them as tabs. */
    public List<Template> templates(CatalogFilter filter) {
        return templates.stream().filter(filter::matches).toList();
    }

    private static long order(@Nullable Long sortOrder) {
        return sortOrder != null ? sortOrder : Long.MAX_VALUE;
    }
}

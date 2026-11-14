package com.dokiments.templates.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.templates.application.port.out.TemplateCatalog;
import com.dokiments.templates.domain.Catalog;
import com.dokiments.templates.domain.Catalog.StoredStyle;
import com.dokiments.templates.domain.Catalog.StoredTemplate;
import com.dokiments.templates.domain.TemplateField;
import com.dokiments.templates.domain.TemplateStyle;
import com.dokiments.templates.domain.TemplateTier;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * The catalog in the {@code templateStyles} and {@code templates} collections, in the shape the web app's seed script
 * writes it. Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text (a
 * field's type as {@code text}), an unknown tier as {@code free}, and a missing order puts the entry last.
 *
 * <p>The catalog is reused for {@code dokiments.templates.catalog-cache-time} (see {@link CatalogCache}).
 */
@Repository
class FirestoreTemplateCatalog implements TemplateCatalog {

    static final String STYLES = "templateStyles";
    static final String TEMPLATES = "templates";

    private final Firestore firestore;
    private final CatalogCache cache;

    FirestoreTemplateCatalog(Firestore firestore, TemplateCatalogProperties properties, Clock clock) {
        this.firestore = firestore;
        this.cache = new CatalogCache(properties.catalogCacheTime(), clock);
    }

    @Override
    public Catalog current() {
        return cache.get(this::read);
    }

    private Catalog read() {
        var styles = FirestoreCalls.await(firestore.collection(STYLES).get()).getDocuments().stream()
                .map(FirestoreTemplateCatalog::style)
                .toList();
        var templates = FirestoreCalls.await(firestore.collection(TEMPLATES).get()).getDocuments().stream()
                .map(FirestoreTemplateCatalog::template)
                .toList();
        return Catalog.of(styles, templates);
    }

    private static StoredStyle style(DocumentSnapshot document) {
        return new StoredStyle(
                new TemplateStyle(
                        document.getId(),
                        text(document.get("name")),
                        text(document.get("description")),
                        TemplateTier.fromStored(text(document.get("tier")))),
                active(document),
                order(document));
    }

    private static StoredTemplate template(DocumentSnapshot document) {
        return new StoredTemplate(
                document.getId(),
                text(document.get("name")),
                text(document.get("description")),
                text(document.get("documentType")),
                fields(document.get("fields")),
                text(document.get("styleId")),
                TemplateTier.fromStored(text(document.get("tier"))),
                document.get("createdAt") instanceof Timestamp createdAt ? FirestoreCalls.toInstant(createdAt) : null,
                active(document),
                order(document));
    }

    /** The form's fields. An entry that isn't a map reads as a field with nothing set, as in the web app. */
    private static List<TemplateField> fields(@Nullable Object value) {
        if (!(value instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> {
                    Map<?, ?> field = entry instanceof Map<?, ?> map ? map : Map.of();
                    return new TemplateField(
                            text(field.get("key")),
                            text(field.get("label")),
                            field.get("placeholder") instanceof String placeholder ? placeholder : null,
                            field.get("type") instanceof String type ? type : "text");
                })
                .toList();
    }

    private static String text(@Nullable Object value) {
        return value instanceof String text ? text : "";
    }

    /** Only {@code isActive: true} shows an entry; anything else hides it. */
    private static boolean active(DocumentSnapshot document) {
        return Boolean.TRUE.equals(document.get("isActive"));
    }

    /** Only a whole number counts as an order, as in the web app. */
    private static @Nullable Long order(DocumentSnapshot document) {
        return document.get("sortOrder") instanceof Long order ? order : null;
    }
}

package com.dokiments.templates;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A template copy in the shape the web app stores it, as plain maps and lists, for every module that keeps one with its
 * data (a document, a shared copy). One place, so the copies are stored and read alike everywhere.
 *
 * <p>Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text, a field's
 * type as {@code text}, and a missing document type, style and tier as {@code contract}, {@code classic} and
 * {@code free}.
 */
public final class StoredTemplateSnapshot {

    private StoredTemplateSnapshot() {}

    /** The stored form: a map with the template's name, description, document type, fields and style. */
    public static Map<String, Object> of(TemplateSnapshot snapshot) {
        var style = snapshot.style();
        return Map.of(
                "description", snapshot.description(),
                "documentType", snapshot.documentType(),
                "fields",
                        snapshot.fields().stream()
                                .map(StoredTemplateSnapshot::field)
                                .toList(),
                "name", snapshot.name(),
                "style",
                        Map.of(
                                "description", style.description(),
                                "id", style.id(),
                                "name", style.name(),
                                "tier", style.tier()));
    }

    /** A copy as stored, whatever was stored. */
    public static TemplateSnapshot read(@Nullable Object stored) {
        Map<?, ?> snapshot = map(stored);
        Map<?, ?> style = map(snapshot.get("style"));
        List<TemplateSnapshot.Field> fields = snapshot.get("fields") instanceof List<?> list
                ? list.stream().map(StoredTemplateSnapshot::field).toList()
                : List.of();
        return new TemplateSnapshot(
                text(snapshot.get("name"), ""),
                text(snapshot.get("description"), ""),
                text(snapshot.get("documentType"), "contract"),
                fields,
                new TemplateSnapshot.Style(
                        text(style.get("id"), "classic"),
                        text(style.get("name"), ""),
                        text(style.get("description"), ""),
                        text(style.get("tier"), "free")));
    }

    /** A field, with the placeholder only when there is one, as the web app writes it. */
    private static Map<String, Object> field(TemplateSnapshot.Field field) {
        var stored = new HashMap<String, Object>();
        stored.put("key", field.key());
        stored.put("label", field.label());
        var placeholder = field.placeholder();
        if (placeholder != null) {
            stored.put("placeholder", placeholder);
        }
        stored.put("type", field.type());
        return stored;
    }

    private static TemplateSnapshot.Field field(@Nullable Object stored) {
        Map<?, ?> field = map(stored);
        return new TemplateSnapshot.Field(
                text(field.get("key"), ""),
                text(field.get("label"), ""),
                field.get("placeholder") instanceof String placeholder ? placeholder : null,
                text(field.get("type"), "text"));
    }

    private static Map<?, ?> map(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(@Nullable Object value, String otherwise) {
        return value instanceof String text ? text : otherwise;
    }
}

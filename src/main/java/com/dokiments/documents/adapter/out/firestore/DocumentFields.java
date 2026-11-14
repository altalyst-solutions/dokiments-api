package com.dokiments.documents.adapter.out.firestore;

import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.UserDocument;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.templates.TemplateSnapshot;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document as stored in {@code users/{uid}/documents/{documentId}}: {@code name} and {@code templateId} as text,
 * {@code templateSnapshot} and {@code values} as maps, and {@code createdAt} and {@code updatedAt} as timestamps. Field
 * names and types are fixed by the documents the web app already wrote.
 *
 * <p>Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text, a missing
 * time as {@code 0}, and in a template copy a missing document type as {@code contract}, style as {@code classic} and
 * tier as {@code free}. A document without a template copy reads without one.
 */
final class DocumentFields {

    private static final String NAME = "name";
    private static final String TEMPLATE_ID = "templateId";
    private static final String TEMPLATE_SNAPSHOT = "templateSnapshot";
    private static final String VALUES = "values";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    private DocumentFields() {}

    static Map<String, Object> of(UserDocument document) {
        var fields = new HashMap<String, Object>();
        fields.put(NAME, document.name());
        fields.put(TEMPLATE_ID, document.templateId());
        var snapshot = document.templateSnapshot();
        if (snapshot != null) {
            fields.put(TEMPLATE_SNAPSHOT, snapshot(snapshot));
        }
        fields.put(VALUES, document.values());
        fields.put(CREATED_AT, FirestoreCalls.toTimestamp(document.createdAt()));
        fields.put(UPDATED_AT, FirestoreCalls.toTimestamp(document.updatedAt()));
        return fields;
    }

    /** Only what changes, and the time of the change. The values are replaced as a whole. */
    static Map<String, Object> of(DocumentChanges changes, Instant changedAt) {
        var fields = new HashMap<String, Object>();
        var name = changes.name();
        if (name != null) {
            fields.put(NAME, name);
        }
        var values = changes.values();
        if (values != null) {
            fields.put(VALUES, values);
        }
        fields.put(UPDATED_AT, FirestoreCalls.toTimestamp(changedAt));
        return fields;
    }

    static UserDocument toDocument(DocumentSnapshot document) {
        return new UserDocument(
                new DocumentId(document.getId()),
                text(document.get(NAME), ""),
                text(document.get(TEMPLATE_ID), ""),
                document.contains(TEMPLATE_SNAPSHOT) ? snapshot(document.get(TEMPLATE_SNAPSHOT)) : null,
                values(document.get(VALUES)),
                time(document.get(CREATED_AT)),
                time(document.get(UPDATED_AT)));
    }

    private static Map<String, Object> snapshot(TemplateSnapshot snapshot) {
        var style = snapshot.style();
        return Map.of(
                "description", snapshot.description(),
                "documentType", snapshot.documentType(),
                "fields", snapshot.fields().stream().map(DocumentFields::field).toList(),
                "name", snapshot.name(),
                "style",
                        Map.of(
                                "description", style.description(),
                                "id", style.id(),
                                "name", style.name(),
                                "tier", style.tier()));
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

    private static TemplateSnapshot snapshot(@Nullable Object stored) {
        Map<?, ?> snapshot = stored instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> style = snapshot.get("style") instanceof Map<?, ?> map ? map : Map.of();
        List<TemplateSnapshot.Field> fields = snapshot.get("fields") instanceof List<?> list
                ? list.stream().map(DocumentFields::field).toList()
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

    private static TemplateSnapshot.Field field(@Nullable Object stored) {
        Map<?, ?> field = stored instanceof Map<?, ?> map ? map : Map.of();
        return new TemplateSnapshot.Field(
                text(field.get("key"), ""),
                text(field.get("label"), ""),
                field.get("placeholder") instanceof String placeholder ? placeholder : null,
                text(field.get("type"), "text"));
    }

    /** The values, with any that isn't text read as empty text. */
    private static Map<String, String> values(@Nullable Object stored) {
        var values = new LinkedHashMap<String, String>();
        if (stored instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), text(value, "")));
        }
        return values;
    }

    private static String text(@Nullable Object value, String otherwise) {
        return value instanceof String text ? text : otherwise;
    }

    private static Instant time(@Nullable Object value) {
        return value instanceof Timestamp timestamp ? FirestoreCalls.toInstant(timestamp) : Instant.EPOCH;
    }
}

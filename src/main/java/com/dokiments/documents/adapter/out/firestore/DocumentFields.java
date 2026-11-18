package com.dokiments.documents.adapter.out.firestore;

import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentId;
import com.dokiments.documents.domain.UserDocument;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.templates.StoredTemplateSnapshot;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document as stored in {@code users/{uid}/documents/{documentId}}: {@code name} and {@code templateId} as text,
 * {@code templateSnapshot} and {@code values} as maps, and {@code createdAt} and {@code updatedAt} as timestamps. Field
 * names and types are fixed by the documents the web app already wrote.
 *
 * <p>Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text, a missing
 * time as {@code 0}; the template copy is read by {@link StoredTemplateSnapshot}. A document without a template copy
 * reads without one.
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
            fields.put(TEMPLATE_SNAPSHOT, StoredTemplateSnapshot.of(snapshot));
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
                text(document.get(NAME)),
                text(document.get(TEMPLATE_ID)),
                document.contains(TEMPLATE_SNAPSHOT)
                        ? StoredTemplateSnapshot.read(document.get(TEMPLATE_SNAPSHOT))
                        : null,
                values(document.get(VALUES)),
                time(document.get(CREATED_AT)),
                time(document.get(UPDATED_AT)));
    }

    /** The values, with any that isn't text read as empty text. */
    private static Map<String, String> values(@Nullable Object stored) {
        var values = new LinkedHashMap<String, String>();
        if (stored instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), text(value)));
        }
        return values;
    }

    private static String text(@Nullable Object value) {
        return value instanceof String text ? text : "";
    }

    private static Instant time(@Nullable Object value) {
        return value instanceof Timestamp timestamp ? FirestoreCalls.toInstant(timestamp) : Instant.EPOCH;
    }
}

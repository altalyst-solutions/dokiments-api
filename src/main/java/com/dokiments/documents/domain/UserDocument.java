package com.dokiments.documents.domain;

import com.dokiments.templates.TemplateSnapshot;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document a user made from a template.
 *
 * <p>The record accepts any values, because documents stored before today's rules must still be readable. What is
 * written goes through {@link DocumentContent}.
 *
 * @param id the document's ID
 * @param name the name the user gave it
 * @param templateId the template it was made from, kept to make another one like it
 * @param templateSnapshot the template as it was when the document was made, which it is shown with; missing for
 *     documents made before copies were kept
 * @param values what the user filled in, by field key
 * @param createdAt when it was made
 * @param updatedAt when it last changed
 */
public record UserDocument(
        DocumentId id,
        String name,
        String templateId,
        @Nullable TemplateSnapshot templateSnapshot,
        Map<String, String> values,
        Instant createdAt,
        Instant updatedAt) {

    public UserDocument {
        // In the order given, which keeps the form's order when it is shown as stored.
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

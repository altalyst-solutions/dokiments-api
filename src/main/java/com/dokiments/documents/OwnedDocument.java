package com.dokiments.documents;

import com.dokiments.templates.TemplateSnapshot;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One of a user's documents, as other modules see it, for example to share a copy of it.
 *
 * @param id the document's ID
 * @param name the name the user gave it
 * @param templateId the template it was made from
 * @param values what the user filled in, by field key
 * @param templateSnapshot the template as it was when the document was made; missing for older documents
 */
public record OwnedDocument(
        String id,
        String name,
        String templateId,
        Map<String, String> values,
        @Nullable TemplateSnapshot templateSnapshot) {

    public OwnedDocument {
        values = Map.copyOf(values);
    }
}

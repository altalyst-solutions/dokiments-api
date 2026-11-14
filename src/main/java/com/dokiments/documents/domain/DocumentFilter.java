package com.dokiments.documents.domain;

import com.dokiments.shared.error.InvalidRequestException;
import org.jspecify.annotations.Nullable;

/**
 * Which documents to list: those made from one template, or all of them.
 *
 * @param templateId only documents made from this template, or all when {@code null}
 */
public record DocumentFilter(@Nullable String templateId) {

    /** The web app's wording, the same as {@code platform.web.ErrorMessages.INVALID_QUERY}. */
    static final String INVALID_QUERY = "Invalid query parameters.";

    /**
     * The filter from the query. A template named as empty text is refused, as in the web app.
     *
     * @throws InvalidRequestException if the template is empty
     */
    public static DocumentFilter of(@Nullable String templateId) {
        if (templateId != null && templateId.isEmpty()) {
            throw new InvalidRequestException(INVALID_QUERY);
        }
        return new DocumentFilter(templateId);
    }

    /** Whether a document belongs in the list. */
    public boolean matches(UserDocument document) {
        return templateId == null || templateId.equals(document.templateId());
    }
}

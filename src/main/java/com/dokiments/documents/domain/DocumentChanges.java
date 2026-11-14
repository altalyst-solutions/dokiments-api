package com.dokiments.documents.domain;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A change to a document: a new name, new values, or both. The values are replaced as a whole. The template a document
 * was made from, and its copy of that template, never change.
 *
 * @param name the new name, or {@code null} to keep it
 * @param values the new values, or {@code null} to keep them
 */
public record DocumentChanges(
        @Nullable String name, @Nullable Map<String, String> values) {

    /**
     * A change as sent, checked as the web app checks it. At least one of the two must be given.
     *
     * @throws InvalidDocumentException if a rule is broken, or nothing is given
     */
    public static DocumentChanges of(@Nullable String name, @Nullable Map<String, ? extends @Nullable String> values) {
        if (name == null && values == null) {
            throw new InvalidDocumentException();
        }
        return new DocumentChanges(
                name == null ? null : DocumentContent.name(name),
                values == null ? null : DocumentContent.values(values));
    }
}

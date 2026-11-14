package com.dokiments.documents.adapter.in.web;

import com.dokiments.documents.domain.UserDocument;
import com.dokiments.templates.TemplateSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document as the web app shows it, with the times in milliseconds since 1970.
 *
 * @param templateSnapshot the template as it was when the document was made; left out for documents made before copies
 *     were kept
 * @param values what the user filled in, by field key
 */
record DocumentResponse(
        long createdAt,
        @Schema(example = "doc_mhx2k1q8_4f9a2c") String id,
        String name,
        @Schema(example = "classic-invoice") String templateId,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SnapshotResponse templateSnapshot,
        long updatedAt,
        Map<String, String> values) {

    static DocumentResponse of(UserDocument document) {
        var snapshot = document.templateSnapshot();
        return new DocumentResponse(
                document.createdAt().toEpochMilli(),
                document.id().value(),
                document.name(),
                document.templateId(),
                snapshot == null ? null : SnapshotResponse.of(snapshot),
                document.updatedAt().toEpochMilli(),
                document.values());
    }

    // These records have their own names, because the API description names schemas after their class, and the
    // templates module already has a FieldResponse and a StyleResponse.

    /** The template's copy: what the document looks like and which form it has. */
    record SnapshotResponse(
            String description,
            @Schema(example = "invoice") String documentType,
            List<SnapshotFieldResponse> fields,
            String name,
            SnapshotStyleResponse style) {

        static SnapshotResponse of(TemplateSnapshot snapshot) {
            var style = snapshot.style();
            return new SnapshotResponse(
                    snapshot.description(),
                    snapshot.documentType(),
                    snapshot.fields().stream().map(SnapshotFieldResponse::of).toList(),
                    snapshot.name(),
                    new SnapshotStyleResponse(style.description(), style.id(), style.name(), style.tier()));
        }
    }

    /**
     * A field of the form.
     *
     * @param placeholder left out when the template has none
     */
    record SnapshotFieldResponse(
            String key,
            String label,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String placeholder,

            @Schema(example = "text") String type) {

        static SnapshotFieldResponse of(TemplateSnapshot.Field field) {
            return new SnapshotFieldResponse(field.key(), field.label(), field.placeholder(), field.type());
        }
    }

    /** The template's style. */
    record SnapshotStyleResponse(
            String description,
            @Schema(example = "classic") String id,
            String name,
            @Schema(example = "free") String tier) {}
}

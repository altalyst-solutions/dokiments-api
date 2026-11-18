package com.dokiments.sharing.adapter.in.web;

import com.dokiments.sharing.domain.Recipient;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.templates.TemplateSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A shared copy as the web app shows it, with every time in milliseconds since 1970.
 *
 * @param recipients everyone it is or was shared with, each with how far they have got
 * @param signatures the signatures, by the signer's ID
 * @param views first openings, by the viewer's ID
 * @param templateSnapshot the document's copy of its template; left out when it has none
 */
record SharedDocumentResponse(
        long createdAt,
        @Schema(example = "uid123_doc_mhx2k1q8_4f9a2c") String id,
        boolean locked,
        String name,
        String ownerEmail,
        String ownerId,
        String ownerName,
        List<SharedRecipientResponse> recipients,
        Map<String, SignatureResponse> signatures,
        String sourceDocumentId,
        String templateId,
        long updatedAt,
        Map<String, String> values,
        Map<String, ViewResponse> views,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable SharedSnapshotResponse templateSnapshot) {

    static SharedDocumentResponse of(SharedDocument document) {
        var signatures = new LinkedHashMap<String, SignatureResponse>();
        document.signatures()
                .forEach((uid, signature) -> signatures.put(
                        uid,
                        new SignatureResponse(
                                signature.email(), millis(signature.signedAt()), signature.type(), signature.value())));
        var views = new LinkedHashMap<String, ViewResponse>();
        document.views()
                .forEach((uid, view) -> views.put(uid, new ViewResponse(view.email(), millis(view.viewedAt()))));
        var snapshot = document.templateSnapshot();
        return new SharedDocumentResponse(
                millis(document.createdAt()),
                document.id(),
                document.locked(),
                document.name(),
                document.ownerEmail(),
                document.ownerId(),
                document.ownerName(),
                document.recipients().stream().map(SharedRecipientResponse::of).toList(),
                signatures,
                document.sourceDocumentId(),
                document.templateId(),
                millis(document.updatedAt()),
                document.values(),
                views,
                snapshot == null ? null : SharedSnapshotResponse.of(snapshot));
    }

    private static long millis(Instant instant) {
        return instant.toEpochMilli();
    }

    /**
     * Someone the copy is or was shared with.
     *
     * @param emailedAt when they were emailed about it, within the last day; left out when never
     * @param revokedAt when the owner took it back; left out unless so
     * @param signedAt when they signed; left out unless so
     * @param uid their account, when linked; left out otherwise
     */
    record SharedRecipientResponse(
            String email,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<Long> emailedAt,

            long invitedAt,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long revokedAt,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long signedAt,

            @Schema(allowableValues = {"pending", "viewed", "signed", "revoked"})
            String status,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String uid) {

        static SharedRecipientResponse of(Recipient recipient) {
            var revokedAt = recipient.revokedAt();
            var signedAt = recipient.signedAt();
            return new SharedRecipientResponse(
                    recipient.email(),
                    recipient.emailedAt().isEmpty()
                            ? null
                            : recipient.emailedAt().stream()
                                    .map(Instant::toEpochMilli)
                                    .toList(),
                    recipient.invitedAt().toEpochMilli(),
                    revokedAt == null ? null : revokedAt.toEpochMilli(),
                    signedAt == null ? null : signedAt.toEpochMilli(),
                    recipient.status().value(),
                    recipient.uid());
        }
    }

    /** A recipient's typed signature. */
    record SignatureResponse(
            String email,
            long signedAt,
            @Schema(allowableValues = "typed") String type,
            String value) {}

    /** A recipient's first opening. */
    record ViewResponse(String email, long viewedAt) {}

    /** The document's copy of its template. */
    record SharedSnapshotResponse(
            String description,
            String documentType,
            List<SharedSnapshotFieldResponse> fields,
            String name,
            SharedSnapshotStyleResponse style) {

        static SharedSnapshotResponse of(TemplateSnapshot snapshot) {
            var style = snapshot.style();
            return new SharedSnapshotResponse(
                    snapshot.description(),
                    snapshot.documentType(),
                    snapshot.fields().stream()
                            .map(field -> new SharedSnapshotFieldResponse(
                                    field.key(), field.label(), field.placeholder(), field.type()))
                            .toList(),
                    snapshot.name(),
                    new SharedSnapshotStyleResponse(style.description(), style.id(), style.name(), style.tier()));
        }
    }

    /** A field of the template's form; the placeholder is left out when there is none. */
    record SharedSnapshotFieldResponse(
            String key,
            String label,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String placeholder,

            String type) {}

    /** The template's style. */
    record SharedSnapshotStyleResponse(String description, String id, String name, String tier) {}
}

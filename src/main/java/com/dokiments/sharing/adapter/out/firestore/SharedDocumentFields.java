package com.dokiments.sharing.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.sharing.domain.Recipient;
import com.dokiments.sharing.domain.RecipientStatus;
import com.dokiments.sharing.domain.ShareLedgerEntry;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.sharing.domain.Signature;
import com.dokiments.sharing.domain.View;
import com.dokiments.templates.StoredTemplateSnapshot;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Shared copies as stored in {@code sharedDocuments/{id}}, and ledger entries in
 * {@code users/{uid}/shareRecipients/{email}}, in the shape the web app writes. Besides the copy's own fields, a copy
 * keeps {@code recipientEmails} and {@code recipientUids}: the recipients with access, flat, so a recipient's copies
 * can be found with one query.
 *
 * <p>Reading is lenient, as the web app's is: a missing field, or one of another type, reads as empty text, a missing
 * time as the start of 1970 and an unknown status as {@code pending}; the template copy is read by
 * {@link StoredTemplateSnapshot}.
 */
final class SharedDocumentFields {

    static final String OWNER_ID = "ownerId";
    static final String RECIPIENT_EMAILS = "recipientEmails";
    static final String RECIPIENT_UIDS = "recipientUids";
    static final String RECIPIENTS = "recipients";
    static final String UPDATED_AT = "updatedAt";

    private SharedDocumentFields() {}

    /** Every field of a copy, to replace it as a whole. */
    static Map<String, Object> of(SharedDocument document) {
        var fields = new HashMap<String, Object>();
        fields.put("createdAt", timestamp(document.createdAt()));
        fields.put("locked", document.locked());
        fields.put("name", document.name());
        fields.put("ownerEmail", document.ownerEmail());
        fields.put(OWNER_ID, document.ownerId());
        fields.put("ownerName", document.ownerName());
        fields.putAll(recipientFields(document));
        fields.put("signatures", signatures(document.signatures()));
        fields.put("sourceDocumentId", document.sourceDocumentId());
        fields.put("templateId", document.templateId());
        fields.put(UPDATED_AT, timestamp(document.updatedAt()));
        fields.put("values", document.values());
        if (!document.views().isEmpty()) {
            fields.put("views", views(document.views()));
        }
        var snapshot = document.templateSnapshot();
        if (snapshot != null) {
            fields.put("templateSnapshot", StoredTemplateSnapshot.of(snapshot));
        }
        return fields;
    }

    /** The recipient fields and the time of the change, which is all that taking a copy back changes. */
    static Map<String, Object> recipientChange(SharedDocument document) {
        Map<String, Object> fields = new HashMap<>(recipientFields(document));
        fields.put(UPDATED_AT, timestamp(document.updatedAt()));
        return fields;
    }

    private static Map<String, Object> recipientFields(SharedDocument document) {
        return Map.of(
                RECIPIENT_EMAILS,
                document.recipientEmails(),
                RECIPIENT_UIDS,
                document.recipientUids(),
                RECIPIENTS,
                document.recipients().stream()
                        .map(SharedDocumentFields::recipient)
                        .toList());
    }

    static Map<String, Object> of(ShareLedgerEntry entry) {
        return Map.of(
                "email",
                entry.email(),
                "firstSharedAt",
                timestamp(entry.firstSharedAt()),
                "lastSharedAt",
                timestamp(entry.lastSharedAt()));
    }

    static SharedDocument toDocument(DocumentSnapshot document) {
        Map<String, Signature> signatures = new LinkedHashMap<>();
        map(document.get("signatures")).forEach((uid, value) -> signatures.put(String.valueOf(uid), signature(value)));
        Map<String, View> views = new LinkedHashMap<>();
        map(document.get("views")).forEach((uid, value) -> views.put(String.valueOf(uid), view(value)));
        Map<String, String> values = new LinkedHashMap<>();
        map(document.get("values")).forEach((key, value) -> values.put(String.valueOf(key), text(value)));
        List<Recipient> recipients = document.get(RECIPIENTS) instanceof List<?> list
                ? list.stream().map(SharedDocumentFields::recipient).toList()
                : List.of();
        return new SharedDocument(
                        document.getId(),
                        time(document.get("createdAt")),
                        Boolean.TRUE.equals(document.get("locked")),
                        text(document.get("name")),
                        text(document.get("ownerEmail")),
                        text(document.get(OWNER_ID)),
                        text(document.get("ownerName")),
                        recipients,
                        signatures,
                        text(document.get("sourceDocumentId")),
                        text(document.get("templateId")),
                        time(document.get(UPDATED_AT)),
                        values,
                        views,
                        document.contains("templateSnapshot")
                                ? StoredTemplateSnapshot.read(document.get("templateSnapshot"))
                                : null)
                .withStatuses();
    }

    static ShareLedgerEntry toLedgerEntry(DocumentSnapshot entry) {
        // The entry's ID is the address, as the web app keys it.
        return new ShareLedgerEntry(
                entry.getId().toLowerCase(Locale.ROOT),
                time(entry.get("firstSharedAt")),
                time(entry.get("lastSharedAt")));
    }

    // Recipients, signatures and views

    private static Map<String, Object> recipient(Recipient recipient) {
        var fields = new HashMap<String, Object>();
        fields.put("email", recipient.email());
        fields.put("invitedAt", timestamp(recipient.invitedAt()));
        fields.put("status", recipient.status().value());
        if (!recipient.emailedAt().isEmpty()) {
            fields.put(
                    "emailedAt",
                    recipient.emailedAt().stream()
                            .map(SharedDocumentFields::timestamp)
                            .toList());
        }
        if (recipient.revokedAt() != null) {
            fields.put("revokedAt", timestamp(recipient.revokedAt()));
        }
        if (recipient.signedAt() != null) {
            fields.put("signedAt", timestamp(recipient.signedAt()));
        }
        if (recipient.uid() != null && !recipient.uid().isEmpty()) {
            fields.put("uid", recipient.uid());
        }
        return fields;
    }

    private static Recipient recipient(@Nullable Object stored) {
        Map<?, ?> fields = map(stored);
        List<Instant> emailedAt = fields.get("emailedAt") instanceof List<?> list
                ? list.stream().map(SharedDocumentFields::time).toList()
                : List.of();
        return new Recipient(
                text(fields.get("email")),
                emailedAt,
                time(fields.get("invitedAt")),
                RecipientStatus.fromStored(text(fields.get("status"))),
                fields.containsKey("revokedAt") ? time(fields.get("revokedAt")) : null,
                fields.containsKey("signedAt") ? time(fields.get("signedAt")) : null,
                fields.get("uid") instanceof String uid ? uid : null);
    }

    private static Map<String, Object> signatures(Map<String, Signature> signatures) {
        var stored = new HashMap<String, Object>();
        signatures.forEach((uid, signature) -> stored.put(
                uid,
                Map.of(
                        "email", signature.email(),
                        "signedAt", timestamp(signature.signedAt()),
                        "type", signature.type(),
                        "value", signature.value())));
        return stored;
    }

    private static Signature signature(@Nullable Object stored) {
        Map<?, ?> fields = map(stored);
        return new Signature(
                text(fields.get("email")), time(fields.get("signedAt")), Signature.TYPED, text(fields.get("value")));
    }

    private static Map<String, Object> views(Map<String, View> views) {
        var stored = new HashMap<String, Object>();
        views.forEach(
                (uid, view) -> stored.put(uid, Map.of("email", view.email(), "viewedAt", timestamp(view.viewedAt()))));
        return stored;
    }

    private static View view(@Nullable Object stored) {
        Map<?, ?> fields = map(stored);
        return new View(text(fields.get("email")), time(fields.get("viewedAt")));
    }

    // Values

    private static Map<?, ?> map(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(@Nullable Object value) {
        return value instanceof String text ? text : "";
    }

    private static Instant time(@Nullable Object value) {
        return value instanceof Timestamp timestamp ? FirestoreCalls.toInstant(timestamp) : Instant.EPOCH;
    }

    private static Timestamp timestamp(Instant instant) {
        return FirestoreCalls.toTimestamp(instant);
    }
}

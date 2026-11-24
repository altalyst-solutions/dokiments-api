package com.dokiments.sharing.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.sharing.application.port.out.InviteStore;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.templates.StoredTemplateSnapshot;
import com.dokiments.templates.TemplateSnapshot;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Invitations in the top-level {@code sharedInvites} collection, as the web app keeps them: the document ID is the
 * SHA-256 of the link's secret, so knowing the ID is the same as holding the link, and anyone with a link can look it
 * up. Reading is lenient, as everywhere: a missing field reads as empty text and a missing time as the start of 1970,
 * which makes an invitation expired.
 */
@Repository
class FirestoreInvites implements InviteStore {

    private static final String OWNER_ID = "ownerId";
    private static final String SHARED_DOCUMENT_ID = "sharedDocumentId";
    private static final String TEMPLATE_SNAPSHOT = "templateSnapshot";

    private final Firestore firestore;

    FirestoreInvites(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void create(Invite invite) {
        Map<String, Object> fields = preview(
                invite.name(),
                invite.ownerEmail(),
                invite.ownerName(),
                invite.templateId(),
                invite.values(),
                invite.templateSnapshot());
        fields.put("createdAt", FirestoreCalls.toTimestamp(invite.createdAt()));
        fields.put("email", invite.email());
        fields.put("expiresAt", FirestoreCalls.toTimestamp(invite.expiresAt()));
        fields.put(OWNER_ID, invite.ownerId());
        fields.put(SHARED_DOCUMENT_ID, invite.sharedDocumentId());
        // create() rather than set(): a secret that somehow came up twice must fail rather than replace an invitation.
        FirestoreCalls.await(invites().document(invite.tokenHash()).create(fields));
    }

    @Override
    public Optional<Invite> find(String tokenHash) {
        var stored = FirestoreCalls.await(invites().document(tokenHash).get());
        return stored.exists() ? Optional.of(invite(stored)) : Optional.empty();
    }

    @Override
    public void refresh(SharedDocument copy) {
        Map<String, Object> fields = preview(
                copy.name(),
                copy.ownerEmail(),
                copy.ownerName(),
                copy.templateId(),
                copy.values(),
                copy.templateSnapshot());
        for (var invite : of(copy.ownerId(), copy.id())) {
            // Only the preview changes: whom it is for, and when it expires, never do.
            FirestoreCalls.await(invite.getReference().update(fields));
        }
    }

    @Override
    public void delete(String tokenHash) {
        FirestoreCalls.await(invites().document(tokenHash).delete());
    }

    @Override
    public void deleteAllFor(String ownerUid, String sharedDocumentId, @Nullable String keepTokenHash) {
        for (var invite : of(ownerUid, sharedDocumentId)) {
            if (!invite.getId().equals(keepTokenHash)) {
                FirestoreCalls.await(invite.getReference().delete());
            }
        }
    }

    /** The owner's invitations to one copy. */
    private List<QueryDocumentSnapshot> of(String ownerUid, String sharedDocumentId) {
        return FirestoreCalls.await(invites()
                        .whereEqualTo(OWNER_ID, ownerUid)
                        .whereEqualTo(SHARED_DOCUMENT_ID, sharedDocumentId)
                        .get())
                .getDocuments();
    }

    /** What the preview shows, as stored, with the template copy only when the document has one. */
    private static Map<String, Object> preview(
            String name,
            String ownerEmail,
            String ownerName,
            String templateId,
            Map<String, String> values,
            @Nullable TemplateSnapshot snapshot) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("ownerEmail", ownerEmail);
        fields.put("ownerName", ownerName);
        fields.put("templateId", templateId);
        fields.put("values", values);
        if (snapshot != null) {
            fields.put(TEMPLATE_SNAPSHOT, StoredTemplateSnapshot.of(snapshot));
        }
        return fields;
    }

    private static Invite invite(DocumentSnapshot stored) {
        Map<String, String> values = new LinkedHashMap<>();
        if (stored.get("values") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), value instanceof String text ? text : ""));
        }
        return new Invite(
                stored.getId(),
                time(stored.get("createdAt")),
                text(stored.get("email")),
                time(stored.get("expiresAt")),
                text(stored.get("name")),
                text(stored.get("ownerEmail")),
                text(stored.get(OWNER_ID)),
                text(stored.get("ownerName")),
                text(stored.get(SHARED_DOCUMENT_ID)),
                text(stored.get("templateId")),
                stored.contains(TEMPLATE_SNAPSHOT) ? StoredTemplateSnapshot.read(stored.get(TEMPLATE_SNAPSHOT)) : null,
                values);
    }

    private CollectionReference invites() {
        return firestore.collection("sharedInvites");
    }

    private static String text(@Nullable Object value) {
        return value instanceof String text ? text : "";
    }

    private static Instant time(@Nullable Object value) {
        return value instanceof Timestamp timestamp ? FirestoreCalls.toInstant(timestamp) : Instant.EPOCH;
    }
}

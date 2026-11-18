package com.dokiments.sharing.domain;

import com.dokiments.templates.TemplateSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A copy of a document, shared with one person at a time for their signature. It is a copy, made when the owner shares,
 * so the owner can go on changing their document without changing what was sent; sharing again refreshes it. Once
 * signed, it is locked and never changes again.
 *
 * @param id {@code <owner's uid>_<document ID>}, so each document has one shared copy
 * @param createdAt when it was first shared
 * @param locked whether it has been signed, after which nothing changes it
 * @param name the document's name
 * @param ownerEmail the owner's address
 * @param ownerId the owner's ID
 * @param ownerName the owner's name
 * @param recipients everyone it is or was shared with, at most one of them with access at a time
 * @param signatures the signatures, by the signer's ID
 * @param sourceDocumentId the owner's document it is a copy of
 * @param templateId the template the document was made from
 * @param updatedAt when it last changed
 * @param values the document's values, without the recipient's signature field
 * @param views first openings, by the viewer's ID
 * @param templateSnapshot the document's copy of its template, if it has one
 */
public record SharedDocument(
        String id,
        Instant createdAt,
        boolean locked,
        String name,
        String ownerEmail,
        String ownerId,
        String ownerName,
        List<Recipient> recipients,
        Map<String, Signature> signatures,
        String sourceDocumentId,
        String templateId,
        Instant updatedAt,
        Map<String, String> values,
        Map<String, View> views,
        @Nullable TemplateSnapshot templateSnapshot) {

    public SharedDocument {
        recipients = List.copyOf(recipients);
        signatures = Collections.unmodifiableMap(new LinkedHashMap<>(signatures));
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        views = Collections.unmodifiableMap(new LinkedHashMap<>(views));
    }

    /** The ID of the shared copy of an owner's document. */
    public static String idFor(String ownerUid, String sourceDocumentId) {
        return ownerUid + "_" + sourceDocumentId;
    }

    /** The recipient who has access now, if any. */
    public Optional<Recipient> activeRecipient() {
        return recipients.stream().filter(Recipient::hasAccess).findFirst();
    }

    /** The addresses with access, which a recipient is found by. */
    public List<String> recipientEmails() {
        return recipients.stream()
                .filter(Recipient::hasAccess)
                .map(Recipient::email)
                .toList();
    }

    /** The linked accounts with access. */
    public List<String> recipientUids() {
        return recipients.stream()
                .filter(Recipient::hasAccess)
                .<String>mapMulti((recipient, keep) -> {
                    if (recipient.uid() != null) {
                        keep.accept(recipient.uid());
                    }
                })
                .toList();
    }

    /**
     * This copy with each recipient's status worked out from the signatures and views, as the web app reads it: a
     * signature makes them {@code signed}, otherwise a view makes them {@code viewed}. A recipient the owner took the
     * copy back from stays {@code revoked}.
     */
    public SharedDocument withStatuses() {
        List<Recipient> resolved = recipients.stream().map(this::resolve).toList();
        return withRecipients(resolved, updatedAt);
    }

    private Recipient resolve(Recipient recipient) {
        if (recipient.status() == RecipientStatus.REVOKED) {
            return recipient;
        }
        var signature = signatures.entrySet().stream()
                .filter(entry -> recipient.is(entry.getKey(), entry.getValue().email()))
                .findFirst();
        if (signature.isPresent()) {
            return recipient.withStatus(
                    RecipientStatus.SIGNED, signature.get().getValue().signedAt());
        }
        boolean viewed = views.entrySet().stream()
                .anyMatch(entry -> recipient.is(entry.getKey(), entry.getValue().email()));
        return recipient.withStatus(viewed ? RecipientStatus.VIEWED : RecipientStatus.PENDING, recipient.signedAt());
    }

    /**
     * This copy taken back from its recipient, by the owner.
     *
     * @throws DocumentLockedException if it has been signed
     * @throws NotSharedException if nobody has access to it
     */
    public SharedDocument revoked(Instant now) {
        if (locked) {
            throw new DocumentLockedException();
        }
        int target = -1;
        for (int i = 0; i < recipients.size() && target < 0; i++) {
            if (recipients.get(i).hasAccess()) {
                target = i;
            }
        }
        if (target < 0) {
            throw new NotSharedException();
        }
        var changed = new ArrayList<>(recipients);
        changed.set(target, recipients.get(target).revoked(now));
        return withRecipients(changed, now);
    }

    SharedDocument withRecipients(List<Recipient> changed, Instant now) {
        return new SharedDocument(
                id,
                createdAt,
                locked,
                name,
                ownerEmail,
                ownerId,
                ownerName,
                changed,
                signatures,
                sourceDocumentId,
                templateId,
                now,
                values,
                views,
                templateSnapshot);
    }
}

package com.dokiments.sharing.domain;

import com.dokiments.templates.TemplateSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * @param recipientUids the accounts that may open it through their ID rather than their address, which an invitation
 *     link adds to; empty once nobody has access
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
        List<String> recipientUids,
        Map<String, Signature> signatures,
        String sourceDocumentId,
        String templateId,
        Instant updatedAt,
        Map<String, String> values,
        Map<String, View> views,
        @Nullable TemplateSnapshot templateSnapshot) {

    public SharedDocument {
        recipients = List.copyOf(recipients);
        recipientUids = List.copyOf(recipientUids);
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

    /**
     * The accounts to store as having access: those already stored, which an invitation link adds to, and those known
     * on the recipients. Empty once nobody has access, so taking a copy back always clears the list.
     */
    static List<String> linkedUids(List<String> stored, List<Recipient> recipients) {
        var linked = new LinkedHashSet<>(stored);
        boolean anyAccess = false;
        for (Recipient recipient : recipients) {
            if (recipient.hasAccess()) {
                anyAccess = true;
                if (recipient.uid() != null && !recipient.uid().isEmpty()) {
                    linked.add(recipient.uid());
                }
            }
        }
        return anyAccess ? List.copyOf(linked) : List.of();
    }

    /** Whether the user is the one who shared it. */
    public boolean isOwnedBy(Viewer viewer) {
        return ownerId.equals(viewer.uid());
    }

    /**
     * Whether the user may open it: its owner, or a recipient, by the rules of the web app's Firestore. Those are an
     * account whose ID is on the copy, or an address on it that Firebase has confirmed is the account owner's, since
     * anyone can create an account under an address that isn't theirs.
     */
    public boolean canBeOpenedBy(Viewer viewer) {
        return isOwnedBy(viewer) || hasAccess(viewer);
    }

    private boolean hasAccess(Viewer viewer) {
        return recipientUids.contains(viewer.uid())
                || (viewer.emailVerified() && recipientEmails().contains(viewer.email()));
    }

    /** Whether the user is a recipient who hasn't been taken back from, by the account's ID or its address. */
    private boolean isRecipient(Viewer viewer) {
        return recipients.stream()
                .anyMatch(recipient -> recipient.hasAccess() && recipient.is(viewer.uid(), viewer.email()));
    }

    /** Whether the user can't sign or have their opening noted: not a recipient the copy can be opened by. */
    private boolean lacksRecipientAccess(Viewer viewer) {
        return !(hasAccess(viewer) && isRecipient(viewer));
    }

    /**
     * This copy once {@code signer} has signed it, which also locks it for good. With one recipient per copy, their
     * signature completes it.
     *
     * @param value what they typed, already checked with {@link Signature#typed}
     * @throws NotRecipientException if the user isn't a recipient who has access, which includes its owner
     * @throws DocumentLockedException if it has been signed already
     * @throws ViewOnlyException if its template has no recipient signature, so it was shared to view
     */
    public SharedDocument signedBy(Viewer signer, String value, Instant now) {
        if (lacksRecipientAccess(signer)) {
            throw new NotRecipientException();
        }
        if (locked) {
            throw new DocumentLockedException();
        }
        if (!SharePolicy.isSignable(templateSnapshot)) {
            throw new ViewOnlyException();
        }
        var signed = new LinkedHashMap<>(signatures);
        signed.put(signer.uid(), new Signature(signer.email(), now, Signature.TYPED, value));
        return new SharedDocument(
                        id,
                        createdAt,
                        true,
                        name,
                        ownerEmail,
                        ownerId,
                        ownerName,
                        recipients,
                        recipientUids,
                        signed,
                        sourceDocumentId,
                        templateId,
                        now,
                        values,
                        views,
                        templateSnapshot)
                .withStatuses();
    }

    /**
     * The opening to note, if this one is the first by that account: a recipient who has access opening a copy that
     * isn't signed. Opening it never counts as a change to the copy.
     */
    public Optional<View> firstViewBy(Viewer viewer, Instant now) {
        if (locked || views.containsKey(viewer.uid()) || lacksRecipientAccess(viewer)) {
            return Optional.empty();
        }
        return Optional.of(new View(viewer.email(), now));
    }

    /**
     * This copy with each recipient's status worked out from the signatures and views, as the web app reads it: a
     * signature makes them {@code signed}, otherwise a view makes them {@code viewed}. A recipient the owner took the
     * copy back from stays {@code revoked}.
     */
    public SharedDocument withStatuses() {
        List<Recipient> resolved = recipients.stream().map(this::resolve).toList();
        return withRecipients(resolved, recipientUids, updatedAt);
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
        return withRecipients(changed, List.of(), now);
    }

    private SharedDocument withRecipients(List<Recipient> changed, List<String> uids, Instant now) {
        return new SharedDocument(
                id,
                createdAt,
                locked,
                name,
                ownerEmail,
                ownerId,
                ownerName,
                changed,
                uids,
                signatures,
                sourceDocumentId,
                templateId,
                now,
                values,
                views,
                templateSnapshot);
    }
}

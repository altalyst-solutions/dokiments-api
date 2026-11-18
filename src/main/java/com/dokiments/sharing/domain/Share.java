package com.dokiments.sharing.domain;

import com.dokiments.documents.OwnedDocument;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Sharing a document with someone: the web app's rules for what the shared copy becomes.
 *
 * @param document the shared copy as it is now
 * @param recipient whom it is shared with
 * @param created whether it is newly shared with them, rather than refreshed for someone who has it already
 */
public record Share(SharedDocument document, Recipient recipient, boolean created) {

    /**
     * The copy after sharing {@code source} with {@code email}. The copy is made afresh from the document, keeping when
     * it was first shared, its signatures and its recipients. Sharing again with the person who has access refreshes
     * their copy; sharing with someone it was taken back from gives them access again, with a clean slate.
     *
     * @param existing the copy as it was, or {@code null} if the document was never shared
     * @param email the address to share with, lower-case
     * @throws DocumentLockedException if the copy has been signed
     * @throws SharedWithAnotherException if someone else has access to it
     */
    public static Share of(
            @Nullable SharedDocument existing,
            OwnedDocument source,
            String ownerUid,
            String ownerEmail,
            String ownerName,
            String email,
            Instant now) {
        if (existing != null && existing.locked()) {
            throw new DocumentLockedException();
        }
        Optional<Recipient> current = existing == null ? Optional.empty() : existing.activeRecipient();
        if (current.isPresent() && !current.get().email().equals(email)) {
            throw new SharedWithAnotherException();
        }

        List<Recipient> before = existing == null ? List.of() : existing.recipients();
        Recipient previous =
                before.stream().filter(r -> r.email().equals(email)).findFirst().orElse(null);
        boolean regainsAccess = previous != null && previous.status() == RecipientStatus.REVOKED;
        Recipient recipient = previous != null && !regainsAccess
                ? previous
                : Recipient.invited(email, previous == null ? List.of() : previous.emailedAt(), now);
        List<Recipient> recipients = new ArrayList<>();
        for (Recipient candidate : before) {
            recipients.add(candidate.email().equals(email) ? recipient : candidate);
        }
        if (previous == null) {
            recipients.add(recipient);
        }

        Map<String, View> views = existing == null ? Map.of() : existing.views();
        if (regainsAccess) {
            // Their earlier opening doesn't count for the new share. The previous recipient is known here, since only
            // they can regain access.
            views = withoutViewsOf(
                    views, email, Objects.requireNonNull(previous).uid());
        }

        Map<String, String> values = new LinkedHashMap<>(source.values());
        values.remove(SharePolicy.RECIPIENT_SIGNATURE_KEY);

        var document = new SharedDocument(
                SharedDocument.idFor(ownerUid, source.id()),
                existing == null ? now : existing.createdAt(),
                false,
                source.name(),
                ownerEmail,
                ownerUid,
                ownerName,
                recipients,
                existing == null ? Map.of() : existing.signatures(),
                source.id(),
                source.templateId(),
                now,
                values,
                views,
                source.templateSnapshot());
        return new Share(document, recipient, existing == null || previous == null || regainsAccess);
    }

    private static Map<String, View> withoutViewsOf(Map<String, View> views, String email, @Nullable String uid) {
        Map<String, View> kept = new LinkedHashMap<>();
        views.forEach((viewer, view) -> {
            if (!view.email().equals(email) && !viewer.equals(uid)) {
                kept.put(viewer, view);
            }
        });
        return kept;
    }
}

package com.dokiments.sharing.domain;

import com.dokiments.templates.TemplateSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An invitation to a shared copy, sent by email to someone who may not have an account yet. Its link lets them preview
 * the document without signing in, and then link the account they create, under the invited address, to the copy.
 *
 * <p>An invitation keeps a preview of the copy (its name, owner, values and template), because someone without an
 * account can't read the copy itself; the owner refreshes it when they share again.
 *
 * @param tokenHash the hash of the link's secret, which is also its document ID
 * @param createdAt when it was sent
 * @param email whom it was sent to, lower-case
 * @param expiresAt when the link stops working, 14 days after it was sent
 * @param name the document's name
 * @param ownerEmail the owner's address
 * @param ownerId the owner's ID
 * @param ownerName the owner's name
 * @param sharedDocumentId the copy it is for
 * @param templateId the document's template
 * @param templateSnapshot the document's copy of its template, if it has one
 * @param values the document's values
 */
public record Invite(
        String tokenHash,
        Instant createdAt,
        String email,
        Instant expiresAt,
        String name,
        String ownerEmail,
        String ownerId,
        String ownerName,
        String sharedDocumentId,
        String templateId,
        @Nullable TemplateSnapshot templateSnapshot,
        Map<String, String> values) {

    /** How long an invitation link works. */
    public static final Duration LIFETIME = Duration.ofDays(14);

    public Invite {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** A new invitation to {@code copy} for {@code email}, from its owner. */
    public static Invite of(SharedDocument copy, String email, String tokenHash, Instant now) {
        return new Invite(
                tokenHash,
                now,
                ShareEmails.normalize(email),
                now.plus(LIFETIME),
                copy.name(),
                copy.ownerEmail(),
                copy.ownerId(),
                copy.ownerName(),
                copy.id(),
                copy.templateId(),
                copy.templateSnapshot(),
                copy.values());
    }

    /** Whether the link still works at {@code now}. */
    public boolean isLive(Instant now) {
        return expiresAt.isAfter(now);
    }

    /**
     * Whether the preview offers to sign, as the web app decides it for an invitation: unless the template copy says it
     * can only be viewed. An invitation without a template copy counts as signable.
     */
    public boolean signable() {
        return templateSnapshot == null || SharePolicy.isSignable(templateSnapshot);
    }
}

package com.dokiments.sharing.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Someone a copy is, or was, shared with.
 *
 * @param email their address, lower-case
 * @param emailedAt when they were emailed about it, within the last day
 * @param invitedAt when it was last shared with them
 * @param status how far they have got
 * @param revokedAt when the owner took it back, if so
 * @param signedAt when they signed, if so
 * @param uid their account, when it has been linked to the share
 */
public record Recipient(
        String email,
        List<Instant> emailedAt,
        Instant invitedAt,
        RecipientStatus status,
        @Nullable Instant revokedAt,
        @Nullable Instant signedAt,
        @Nullable String uid) {

    public Recipient {
        emailedAt = List.copyOf(emailedAt);
    }

    /** Someone it has just been shared with. Emails already sent about the copy still count against the limits. */
    static Recipient invited(String email, List<Instant> emailedAt, Instant now) {
        return new Recipient(email, emailedAt, now, RecipientStatus.PENDING, null, null, null);
    }

    boolean hasAccess() {
        return status != RecipientStatus.REVOKED;
    }

    /** Whether a signature or view by {@code uid} under {@code email} is this recipient's. */
    boolean is(String uid, String email) {
        return uid.equals(this.uid) || email.equals(this.email);
    }

    Recipient withStatus(RecipientStatus status, @Nullable Instant signedAt) {
        return new Recipient(email, emailedAt, invitedAt, status, revokedAt, signedAt, uid);
    }

    Recipient revoked(Instant now) {
        return new Recipient(email, emailedAt, invitedAt, RecipientStatus.REVOKED, now, signedAt, uid);
    }
}

package com.dokiments.sharing.application.service;

import com.dokiments.sharing.application.port.in.NotifyRecipients;
import com.dokiments.sharing.application.port.out.InviteStore;
import com.dokiments.sharing.application.port.out.RecipientNotices;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.DocumentShared;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.InviteToken;
import com.dokiments.sharing.domain.ShareEmailLimits;
import com.dokiments.sharing.domain.SharedDocument;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * What follows a share, as the web app does it once it has answered: the previews in the copy's invitations are brought
 * up to date, and the person it was shared with is emailed an invitation, within the email limits. A step that fails is
 * logged and the others go on: the copy is shared already, and nothing here may undo that.
 */
@Service
class RecipientNoticeService implements NotifyRecipients {

    private static final Logger log = LoggerFactory.getLogger(RecipientNoticeService.class);

    private final SharedDocumentStore copies;
    private final InviteStore invites;
    private final RecipientNotices notices;
    private final Clock clock;
    // The secret in an invitation link, so it must come from a strong source.
    private final RandomGenerator random = new SecureRandom();

    RecipientNoticeService(SharedDocumentStore copies, InviteStore invites, RecipientNotices notices, Clock clock) {
        this.copies = copies;
        this.invites = invites;
        this.notices = notices;
        this.clock = clock;
    }

    @Override
    public void documentShared(DocumentShared event) {
        refreshInvites(event);
        try {
            email(event);
        } catch (RuntimeException e) {
            log.warn("The email about a shared document couldn't be sent", e);
        }
    }

    private void refreshInvites(DocumentShared event) {
        try {
            copies.find(event.sharedDocumentId()).ifPresent(invites::refresh);
        } catch (RuntimeException e) {
            log.warn("The previews in a shared document's invitations couldn't be refreshed", e);
        }
    }

    private void email(DocumentShared event) {
        var shares = copies.ownedBy(event.ownerUid());
        var decision =
                ShareEmailLimits.decide(clock.instant(), event.recipientEmail(), event.sharedDocumentId(), shares);
        if (decision != ShareEmailLimits.Decision.SEND) {
            log.info("The email about a shared document was held back: {}", decision);
            return;
        }

        var invite = shares.stream()
                .filter(share -> share.id().equals(event.sharedDocumentId()))
                .findFirst()
                .map(copy -> invite(copy, event))
                .orElse(null);
        if (!notices.shared(event, invite == null ? null : invite.token())) {
            // The email didn't go, so nobody has the link: the invitation would only wait to expire.
            if (invite != null) {
                quietly(() -> invites.delete(invite.hash()));
            }
            return;
        }

        copies.recordEmail(
                event.sharedDocumentId(),
                event.recipientEmail(),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        if (invite != null) {
            // Only the newest link works, so a forwarded older email can't be used once a new one is sent.
            quietly(() -> invites.deleteAllFor(event.ownerUid(), event.sharedDocumentId(), invite.hash()));
        }
    }

    /** A new invitation for the email, or none if it couldn't be stored; the email then points to the shared list. */
    private @Nullable InviteToken invite(SharedDocument copy, DocumentShared event) {
        try {
            var token = InviteToken.generate(random);
            invites.create(Invite.of(copy, event.recipientEmail(), token.hash(), event.sharedAt()));
            return token;
        } catch (RuntimeException e) {
            log.warn("An invitation for a shared document couldn't be stored", e);
            return null;
        }
    }

    private static void quietly(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException e) {
            log.warn("Old invitations for a shared document couldn't be cleared", e);
        }
    }
}

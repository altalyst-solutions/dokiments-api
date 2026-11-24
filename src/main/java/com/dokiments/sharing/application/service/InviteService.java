package com.dokiments.sharing.application.service;

import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.SignedInUser;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.application.port.in.ManageInvites;
import com.dokiments.sharing.application.port.out.InviteStore;
import com.dokiments.sharing.application.port.out.SharedDocumentStore;
import com.dokiments.sharing.domain.InviteExpiredException;
import com.dokiments.sharing.domain.InviteInvalidException;
import com.dokiments.sharing.domain.InviteMismatchException;
import com.dokiments.sharing.domain.InviteStatus;
import com.dokiments.sharing.domain.InviteToken;
import com.dokiments.sharing.domain.Viewer;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
class InviteService implements ManageInvites {

    private final InviteStore invites;
    private final SharedDocumentStore copies;
    private final boolean enabled;
    private final Clock clock;

    InviteService(InviteStore invites, SharedDocumentStore copies, DokimentsProperties properties, Clock clock) {
        this.invites = invites;
        this.copies = copies;
        this.enabled = properties.features().documentSharing();
        this.clock = clock;
    }

    @Override
    public Preview preview(String token) {
        requireEnabled();
        return lookUp(token, clock.instant());
    }

    @Override
    public String accept(SignedInUser user, String token) {
        requireEnabled();
        Instant now = clock.instant();
        var found = lookUp(token, now);
        var invite = found.invite();
        if (found.status() == InviteStatus.EXPIRED) {
            throw new InviteExpiredException();
        }
        if (invite == null) {
            throw new InviteInvalidException();
        }
        var viewer = Viewer.of(user);
        if (!viewer.email().equals(invite.email())) {
            throw new InviteMismatchException(invite.email());
        }
        // Someone who may open the copy already, such as its owner or a recipient with a verified address, needs no
        // link: they are sent straight to it.
        var copy = copies.find(invite.sharedDocumentId());
        if (copy.isPresent() && copy.get().canBeOpenedBy(viewer)) {
            return copy.get().id();
        }
        if (!copies.acceptInvite(invite, viewer, now)) {
            throw new InviteInvalidException();
        }
        return invite.sharedDocumentId();
    }

    /** The invitation a link names, if its shape is right, it exists, and it hasn't expired. */
    private Preview lookUp(String token, Instant now) {
        if (!InviteToken.hasShape(token)) {
            return new Preview(InviteStatus.INVALID, null);
        }
        return invites.find(InviteToken.hash(token))
                .map(invite -> invite.isLive(now)
                        ? new Preview(InviteStatus.VALID, invite)
                        : new Preview(InviteStatus.EXPIRED, null))
                .orElseGet(() -> new Preview(InviteStatus.INVALID, null));
    }

    /** While sharing is off, its routes don't exist, as in the web app. */
    private void requireEnabled() {
        if (!enabled) {
            throw new NotFoundException();
        }
    }
}

package com.dokiments.sharing.application.port.in;

import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.InviteStatus;
import org.jspecify.annotations.Nullable;

/**
 * Invitation links, which let someone without an account preview a shared copy and then link the account they create.
 * Both answer {@code 404 Not found.} while sharing is switched off ({@code DOCUMENT_SHARING_ENABLED}).
 */
public interface ManageInvites {

    /** What a link leads to, for anyone who has it, signed in or not. */
    Preview preview(String token);

    /**
     * Links the signed-in account to the copy the link is for, and returns the copy's ID.
     *
     * @throws com.dokiments.sharing.domain.InviteExpiredException if the link is more than 14 days old
     * @throws com.dokiments.sharing.domain.InviteInvalidException if it doesn't work, or no longer applies
     * @throws com.dokiments.sharing.domain.InviteMismatchException if the account's address isn't the invited one
     */
    String accept(SignedInUser user, String token);

    /**
     * What a link leads to.
     *
     * @param status whether it works
     * @param invite the invitation, when it works
     */
    record Preview(InviteStatus status, @Nullable Invite invite) {}
}

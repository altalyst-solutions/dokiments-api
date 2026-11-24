package com.dokiments.sharing.domain;

import com.dokiments.shared.error.ForbiddenException;
import java.io.Serial;
import java.util.Map;

/**
 * The signed-in account's address isn't the one the invitation was sent to. Answered with {@code 403}, with the invited
 * address masked ({@code j***@example.com}) next to the message, so the web app can suggest which account to sign in
 * with, without showing the whole address to whoever holds the link.
 */
public class InviteMismatchException extends ForbiddenException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The web app's wording. */
    static final String MESSAGE = "This invitation was sent to a different email address.";

    private final String invitedEmail;

    public InviteMismatchException(String invitedEmail) {
        super(MESSAGE);
        this.invitedEmail = ShareEmails.mask(invitedEmail);
    }

    @Override
    public Map<String, String> publicFields() {
        return Map.of("invitedEmail", invitedEmail);
    }
}

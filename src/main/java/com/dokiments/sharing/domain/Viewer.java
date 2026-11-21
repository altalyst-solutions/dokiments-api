package com.dokiments.sharing.domain;

import com.dokiments.shared.SignedInUser;

/**
 * Who is looking at, or acting on, a shared copy: an account, and the address it signs in with.
 *
 * <p>Firestore's security rules decide this for the web app, which this service doesn't go through, so the same checks
 * are made here from these three facts: an address counts only when it is verified, and an account the invitation link
 * linked counts through its ID.
 *
 * @param uid the account's ID
 * @param email the account's address, lower-case
 * @param emailVerified whether Firebase has confirmed the address belongs to the account's owner
 */
public record Viewer(String uid, String email, boolean emailVerified) {

    /** The signed-in user as a viewer, with the address as sharing compares it. */
    public static Viewer of(SignedInUser user) {
        return new Viewer(user.uid(), ShareEmails.normalize(user.email()), user.emailVerified());
    }
}

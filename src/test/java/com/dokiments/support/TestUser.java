package com.dokiments.support;

import com.dokiments.shared.SignedInUser;

/**
 * A signed-in user for unit tests, without a session.
 *
 * @param uid the user's ID
 * @param email the user's address
 * @param displayName the user's name
 * @param emailVerified whether the address is verified
 */
public record TestUser(String uid, String email, String displayName, boolean emailVerified) implements SignedInUser {

    /** Jane, with a verified address. */
    public static final TestUser JANE = new TestUser("uid-1", "jane@example.com", "Jane", true);

    /** The same user with another name. */
    public TestUser named(String name) {
        return new TestUser(uid, email, name, emailVerified);
    }
}

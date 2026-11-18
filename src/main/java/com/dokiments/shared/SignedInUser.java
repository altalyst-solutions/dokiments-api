package com.dokiments.shared;

/**
 * Who sent a request, as any module may see it: the user's ID, address and name, and whether the address is verified.
 *
 * <p>The session behind it (its Firebase tokens and expiry) stays inside the {@code auth} module. A controller in
 * another module reads the signed-in user with {@code @AuthenticationPrincipal SignedInUser user}, and builds every
 * path to the user's data from {@link #uid()}, never from the request, so a request can only ever reach its sender's
 * own data.
 */
public interface SignedInUser {

    /** The Firebase Auth ID, which is also the ID of the user's profile document. */
    String uid();

    /** The address the user signs in with. */
    String email();

    /** The user's name, as of signing in or the last change to it in the settings. */
    String displayName();

    /**
     * Whether Firebase has confirmed that the user owns {@link #email()}, as of signing in or the last refresh.
     * Anything that trusts the address to decide what the user may see, such as a document shared with it, must check
     * this: anyone can create an account under an address that isn't theirs.
     */
    boolean emailVerified();
}

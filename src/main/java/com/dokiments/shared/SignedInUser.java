package com.dokiments.shared;

/**
 * Who sent a request, as any module may see it: the user's ID, address and name.
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
}

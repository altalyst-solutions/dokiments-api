package com.dokiments.shared;

/**
 * Who sent a request, as any module may see it: only the user's ID.
 *
 * <p>The session behind it (tokens, expiry) stays inside the {@code auth} module. A controller in another module reads
 * the signed-in user with {@code @AuthenticationPrincipal SignedInUser user}, and builds every path to the user's data
 * from {@link #uid()}, never from the request, so a request can only ever reach its sender's own data.
 */
public interface SignedInUser {

    /** The Firebase Auth ID, which is also the ID of the user's profile document. */
    String uid();
}

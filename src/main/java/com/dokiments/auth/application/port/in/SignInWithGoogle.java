package com.dokiments.auth.application.port.in;

import com.dokiments.auth.domain.AuthSession;
import com.dokiments.auth.domain.ReturnPath;
import java.io.Serial;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Signing in with Google, in two steps: the start sends the browser to Google, and Google sends it back to the finish.
 *
 * <p>The first Google sign-in creates the account and its profile. One with the address of an existing password account
 * signs in to that account, which Firebase links to Google.
 */
public interface SignInWithGoogle {

    /**
     * Starts a sign-in.
     *
     * @param next where to go afterwards, as asked for; anything but a path on this site becomes the dashboard
     * @throws Failed if Google sign-in isn't set up here
     */
    Started start(@Nullable String next);

    /**
     * Completes a sign-in, once the {@code state} Google sent back is known to match the one this browser started with.
     *
     * @throws Failed for anything that stops the sign-in: an unknown or used {@code state}, a code Google refuses,
     *     Google or Firebase refusing or unreachable
     */
    Finished finish(String state, String code);

    /**
     * A started sign-in.
     *
     * @param authorizationUrl Google's sign-in page, where the browser goes now
     * @param state the value the browser must bring back
     */
    record Started(URI authorizationUrl, String state) {}

    /**
     * A completed sign-in.
     *
     * @param session the new session
     * @param returnPath where to send the browser
     */
    record Finished(AuthSession session, ReturnPath returnPath) {}

    /** The sign-in can't go ahead. The user is sent back to the sign-in page; the reason is logged only. */
    final class Failed extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public Failed(String reason, @Nullable Throwable cause) {
            super(reason, cause);
        }
    }
}

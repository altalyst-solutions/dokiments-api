package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The caller isn't signed in, or their session has ended, so they have to sign in again.
 *
 * <p>Answered with {@code 401 Unauthorized}.
 */
public non-sealed class UnauthenticatedException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The message the web app expects for a missing or expired session (British spelling, as it is today). */
    public static final String DEFAULT_MESSAGE = "Unauthorised.";

    public UnauthenticatedException() {
        this(DEFAULT_MESSAGE);
    }

    public UnauthenticatedException(String publicMessage) {
        super(publicMessage, null);
    }

    public UnauthenticatedException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The caller is signed in but isn't allowed to do this, for example because their plan doesn't include it.
 *
 * <p>Answered with {@code 403 Forbidden}.
 */
public non-sealed class ForbiddenException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Generic message, for when no more specific reason should be given. */
    public static final String DEFAULT_MESSAGE = "Forbidden.";

    public ForbiddenException() {
        this(DEFAULT_MESSAGE);
    }

    public ForbiddenException(String publicMessage) {
        super(publicMessage, null);
    }

    public ForbiddenException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

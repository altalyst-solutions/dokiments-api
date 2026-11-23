package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * What was asked for existed, but no longer works and never will again, for example an invitation link that has
 * expired. Unlike {@link NotFoundException}, the web app shows the user that it existed.
 *
 * <p>Answered with {@code 410 Gone}.
 */
public non-sealed class GoneException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GoneException(String publicMessage) {
        super(publicMessage, null);
    }

    public GoneException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The resource doesn't exist, or exists but belongs to someone else.
 *
 * <p>Both cases are answered the same way on purpose, so a caller can't find out which IDs exist by trying them.
 *
 * <p>Answered with {@code 404 Not Found}.
 */
public non-sealed class NotFoundException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Generic message, for when the kind of resource shouldn't be named. */
    public static final String DEFAULT_MESSAGE = "Not found.";

    public NotFoundException() {
        this(DEFAULT_MESSAGE);
    }

    public NotFoundException(String publicMessage) {
        super(publicMessage, null);
    }

    public NotFoundException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

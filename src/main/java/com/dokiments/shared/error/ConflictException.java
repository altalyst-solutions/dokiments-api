package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The request is valid but clashes with the current state, for example editing a document that has been signed and
 * locked.
 *
 * <p>Answered with {@code 409 Conflict}.
 */
public non-sealed class ConflictException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(String publicMessage) {
        super(publicMessage, null);
    }

    public ConflictException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

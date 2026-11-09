package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The request itself is wrong: malformed or invalid input that the client has to change before retrying.
 *
 * <p>Answered with {@code 400 Bad Request}.
 */
public non-sealed class InvalidRequestException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String publicMessage) {
        super(publicMessage, null);
    }

    public InvalidRequestException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Too many requests in a short time, so this one is refused for now; for example Firebase throttling the verification
 * emails of one account. Trying again later works.
 *
 * <p>Answered with {@code 429 Too Many Requests}.
 */
public non-sealed class TooManyRequestsException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyRequestsException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

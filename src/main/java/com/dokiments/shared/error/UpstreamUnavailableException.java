package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * An external service, such as Firebase, couldn't be reached at all: connection refused, DNS failure or a timeout.
 *
 * <p>This is deliberately different from that service rejecting the request. Being unreachable says nothing about the
 * credentials or data that were sent, so it must never be reported as {@code 401} or {@code 400}. The failure is
 * usually temporary, and {@code 503} tells the client that trying again later can work. Keep the original error as the
 * {@code cause} so it appears in the logs.
 *
 * <p>Answered with {@code 503 Service Unavailable}.
 */
public non-sealed class UpstreamUnavailableException extends DokimentsException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The message the web app shows when an external service is unreachable. */
    public static final String DEFAULT_MESSAGE = "Couldn't reach an external service. Please try again in a moment.";

    public UpstreamUnavailableException(Throwable cause) {
        this(DEFAULT_MESSAGE, cause);
    }

    public UpstreamUnavailableException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }
}

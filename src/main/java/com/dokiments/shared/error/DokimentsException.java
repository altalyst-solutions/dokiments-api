package com.dokiments.shared.error;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Base type of every expected failure in the application.
 *
 * <p>The message given to the constructor is the <strong>public</strong> message: it is sent to the client as is, in
 * {@code { "error": "..." }}. It must therefore be written for users and must never contain internal details such as a
 * provider's error text, an ID that shouldn't be disclosed, or a stack trace. Diagnostic details belong in the
 * {@code cause}, which is logged but never returned.
 *
 * <p>The hierarchy is sealed so the HTTP mapping in the platform module can be checked by the compiler: adding a new
 * kind of failure here forces that mapping to handle it. Each permitted type is non-sealed, so modules can still add
 * their own, more specific exceptions underneath.
 */
public abstract sealed class DokimentsException extends RuntimeException
        permits InvalidRequestException,
                UnauthenticatedException,
                ForbiddenException,
                NotFoundException,
                ConflictException,
                TooManyRequestsException,
                UpstreamUnavailableException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected DokimentsException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }

    /** The message that is safe to send to the client. */
    public String publicMessage() {
        String message = getMessage();
        return message != null ? message : "";
    }

    /**
     * A code the client acts on, sent next to the message as {@code "code"}, or {@code null} for none. Only for the few
     * failures the web app handles in code rather than by showing the message, such as {@code REAUTH_REQUIRED}.
     */
    public @Nullable String publicCode() {
        return null;
    }
}

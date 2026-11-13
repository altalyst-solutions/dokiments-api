package com.dokiments.auth.domain;

import com.dokiments.shared.error.ForbiddenException;
import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * The session is valid, but too old for this change, such as a new password: the user must type their password again
 * first. The web app recognises the {@code REAUTH_REQUIRED} code and asks for it, then calls
 * {@code /api/auth/reauthenticate}.
 *
 * <p>Answered with {@code 403} and {@code { "code": "REAUTH_REQUIRED", "error": "..." }}.
 */
public class ReauthenticationRequiredException extends ForbiddenException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CODE = "REAUTH_REQUIRED";

    public ReauthenticationRequiredException(String publicMessage, @Nullable Throwable cause) {
        super(publicMessage, cause);
    }

    @Override
    public String publicCode() {
        return CODE;
    }
}

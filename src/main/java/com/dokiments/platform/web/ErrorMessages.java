package com.dokiments.platform.web;

import com.dokiments.shared.error.ForbiddenException;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.shared.error.UpstreamUnavailableException;

/**
 * The generic error messages, worded exactly as the web app's current API returns them.
 *
 * <p>These are used when the failure doesn't come with a more specific message of its own: invalid input, an unknown
 * path, or an unexpected error. Changing the wording changes what users see, so keep them as they are unless the web
 * app changes too.
 */
public final class ErrorMessages {

    /** The body is missing or isn't valid JSON. */
    public static final String INVALID_JSON = "Request body must be valid JSON.";

    /** The body is valid JSON but doesn't match what the endpoint expects. Details are logged, never returned. */
    public static final String INVALID_BODY = "Invalid request body.";

    /** A query parameter is missing, has the wrong type or fails validation. */
    public static final String INVALID_QUERY = "Invalid query parameters.";

    public static final String UNAUTHENTICATED = UnauthenticatedException.DEFAULT_MESSAGE;

    public static final String FORBIDDEN = ForbiddenException.DEFAULT_MESSAGE;

    public static final String NOT_FOUND = NotFoundException.DEFAULT_MESSAGE;

    public static final String METHOD_NOT_ALLOWED = "Method not allowed.";

    public static final String UNSUPPORTED_MEDIA_TYPE = "Unsupported content type.";

    public static final String UPSTREAM_UNAVAILABLE = UpstreamUnavailableException.DEFAULT_MESSAGE;

    /** Anything unexpected. The real error is logged; the client only ever sees this. */
    public static final String UNEXPECTED = "Something went wrong. Please try again.";

    private ErrorMessages() {}

    /**
     * The generic message for a status code, for errors that only come with a status (for example from Spring itself,
     * or a failure outside any controller).
     */
    public static String forStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_BODY;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> UPSTREAM_UNAVAILABLE;
            default -> UNEXPECTED;
        };
    }
}

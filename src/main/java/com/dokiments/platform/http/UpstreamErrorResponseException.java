package com.dokiments.platform.http;

import java.io.Serial;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;

/**
 * An external service answered that it can't handle the request right now ({@code 5xx}, or {@code 429} for too many
 * requests).
 *
 * <p>Only used as the cause of an {@link com.dokiments.shared.error.UpstreamUnavailableException}, so the logs show
 * which service answered what. The message names the method, address and status, without the query or the response
 * body, which can contain keys or personal data.
 */
final class UpstreamErrorResponseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    UpstreamErrorResponseException(HttpRequest request, HttpStatusCode status) {
        super(request.getMethod() + " " + UpstreamRetryInterceptor.describe(request.getURI()) + " answered "
                + status.value());
    }
}

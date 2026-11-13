package com.dokiments.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * The body of every error response: {@code { "error": "..." }}.
 *
 * <p>The web app reads exactly these fields, so no others (timestamps, paths, exception names) are added. The message
 * must always be safe to show to a user. A {@code code} appears only for the few failures the web app handles in code.
 *
 * @param error message for the user
 * @param code a code the web app acts on, such as {@code REAUTH_REQUIRED}; left out when there is none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String error, @Nullable String code) {

    public ErrorBody(String error) {
        this(error, null);
    }
}

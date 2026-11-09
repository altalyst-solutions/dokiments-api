package com.dokiments.platform.web;

/**
 * The body of every error response: {@code { "error": "..." }}.
 *
 * <p>The web app reads exactly this field, so no other fields (timestamps, paths, codes) are added. The message must
 * always be safe to show to a user.
 *
 * @param error message for the user
 */
public record ErrorBody(String error) {}

/**
 * Web plumbing shared by every controller: the error response format and the mapping from exceptions to status codes.
 *
 * <p>Every error the API returns, whatever its source (a controller, request validation, Spring Security, or a failure
 * outside any controller), has the same body: {@code { "error": "<message safe to show the user>" }}. That is the
 * format the web app already relies on, so it must not change.
 */
@NullMarked
package com.dokiments.platform.web;

import org.jspecify.annotations.NullMarked;

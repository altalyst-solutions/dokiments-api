package com.dokiments.auth.domain;

import org.jspecify.annotations.Nullable;

/**
 * Where to send someone after signing in, as asked for by the page they came from.
 *
 * <p>Only a path on this site is accepted: anything else, including {@code //other.site/...}, which a browser reads as
 * an address on another site, becomes the dashboard. Otherwise, a link to the sign-in could send people anywhere once
 * they had signed in.
 *
 * @param path the path, starting with a single {@code /}
 */
public record ReturnPath(String path) {

    public static final ReturnPath DASHBOARD = new ReturnPath("/dashboard");

    public ReturnPath {
        if (!path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) {
            throw new IllegalArgumentException("Not a path on this site");
        }
    }

    /** The path asked for, or the dashboard when it isn't a path on this site. */
    public static ReturnPath of(@Nullable String asked) {
        if (asked == null || !asked.startsWith("/") || asked.startsWith("//") || asked.startsWith("/\\")) {
            return DASHBOARD;
        }
        return new ReturnPath(asked);
    }
}

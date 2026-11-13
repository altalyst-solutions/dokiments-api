package com.dokiments.platform.security;

import java.util.List;

/**
 * Endpoints that change something but need no CSRF token: they use no session, so a forged request could do nothing a
 * direct one couldn't. Every other request that changes something must carry the token.
 */
public final class CsrfExemptions {

    /** The paths, as the security rules and the API description both use them. */
    public static final List<String> PATHS = List.of("/api/newsletter");

    private CsrfExemptions() {}
}

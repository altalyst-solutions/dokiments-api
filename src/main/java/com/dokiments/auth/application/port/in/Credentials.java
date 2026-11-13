package com.dokiments.auth.application.port.in;

/** What the commands share. */
final class Credentials {

    /** The web app's answer to a body that doesn't match what the endpoint expects. */
    static final String INVALID_BODY = "Invalid request body.";

    private Credentials() {}
}

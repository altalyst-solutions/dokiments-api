package com.dokiments.sharing.domain;

import java.time.Instant;

/**
 * A recipient's signature: their name, typed.
 *
 * @param email the signer's address, lower-case
 * @param signedAt when they signed
 * @param type the kind of signature; {@link #TYPED} is the only one
 * @param value what they typed
 */
public record Signature(String email, Instant signedAt, String type, String value) {

    /** The only kind of signature. */
    public static final String TYPED = "typed";
}

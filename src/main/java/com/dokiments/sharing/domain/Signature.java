package com.dokiments.sharing.domain;

import com.dokiments.shared.TypedText;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

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

    /** The longest typed signature, as the web app accepts. */
    public static final int MAX_LENGTH = 200;

    /**
     * What someone typed to sign, as it is kept: trimmed, and neither empty nor longer than {@link #MAX_LENGTH}
     * characters.
     *
     * @throws InvalidShareException if it breaks one of those rules
     */
    public static String typed(@Nullable String typed) {
        if (typed == null) {
            throw new InvalidShareException();
        }
        String value = TypedText.trim(typed);
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidShareException();
        }
        return value;
    }
}

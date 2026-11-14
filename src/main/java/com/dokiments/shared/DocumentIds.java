package com.dokiments.shared;

import java.nio.charset.StandardCharsets;

/**
 * Firestore's rules for a document ID, for IDs that arrive in a URL. An ID that breaks them can't name any document, so
 * it is answered like one that doesn't exist, before it reaches Firestore.
 */
public final class DocumentIds {

    /** Firestore's limit for a document ID, in UTF-8 bytes. */
    private static final int MAX_BYTES = 1500;

    private DocumentIds() {}

    /**
     * Whether a document could have this ID: not empty, at most 1500 bytes, no {@code /}, not {@code .} or {@code ..},
     * and not of the form {@code __name__}, which Firestore reserves.
     */
    public static boolean isStorable(String id) {
        return !id.isEmpty()
                && id.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && id.indexOf('/') < 0
                && !id.equals(".")
                && !id.equals("..")
                && !(id.length() >= 4 && id.startsWith("__") && id.endsWith("__"));
    }
}

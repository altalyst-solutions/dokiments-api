package com.dokiments.clients.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The ID of a client, which is also its document ID in Firestore.
 *
 * <p>New IDs have the web app's form, {@code client_<time>_<random>}, so clients created by either one look alike. An
 * ID arriving in a URL is only accepted when Firestore could store a document under it: anything else can't name a
 * client, so it is treated like an ID that doesn't exist.
 *
 * @param value the ID
 */
public record ClientId(String value) {

    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

    /** Firestore's limit for a document ID, in UTF-8 bytes. */
    private static final int MAX_BYTES = 1500;

    public ClientId {
        if (!isStorable(value)) {
            throw new IllegalArgumentException("Not a storable client ID");
        }
    }

    /** A new ID: the time in base 36 and six random base-36 characters, as the web app makes them. */
    public static ClientId generate(Instant now, RandomGenerator random) {
        var id = new StringBuilder("client_")
                .append(Long.toString(now.toEpochMilli(), 36))
                .append('_');
        for (int i = 0; i < 6; i++) {
            id.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return new ClientId(id.toString());
    }

    /** The ID from a URL, or empty when no client could have it. */
    public static Optional<ClientId> parse(String asked) {
        return isStorable(asked) ? Optional.of(new ClientId(asked)) : Optional.empty();
    }

    /**
     * Firestore's rules for a document ID: not empty, at most 1500 bytes, no {@code /}, not {@code .} or {@code ..},
     * and not of the form {@code __name__}, which Firestore reserves.
     */
    private static boolean isStorable(String id) {
        return !id.isEmpty()
                && id.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && id.indexOf('/') < 0
                && !id.equals(".")
                && !id.equals("..")
                && !(id.length() >= 4 && id.startsWith("__") && id.endsWith("__"));
    }
}

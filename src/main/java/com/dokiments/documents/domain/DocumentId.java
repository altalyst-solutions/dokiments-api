package com.dokiments.documents.domain;

import com.dokiments.shared.DocumentIds;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The ID of a document, which is also its document ID in Firestore. New IDs have the web app's form,
 * {@code doc_<time>_<random>}, so documents created by either one look alike.
 *
 * @param value the ID
 */
public record DocumentId(String value) {

    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";

    public DocumentId {
        if (!DocumentIds.isStorable(value)) {
            throw new IllegalArgumentException("Not a storable document ID");
        }
    }

    /** A new ID: the time in base 36 and six random base-36 characters, as the web app makes them. */
    public static DocumentId generate(Instant now, RandomGenerator random) {
        var id = new StringBuilder("doc_")
                .append(Long.toString(now.toEpochMilli(), 36))
                .append('_');
        for (int i = 0; i < 6; i++) {
            id.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return new DocumentId(id.toString());
    }

    /** The ID from a URL, or empty when no document could have it. */
    public static Optional<DocumentId> parse(String asked) {
        return DocumentIds.isStorable(asked) ? Optional.of(new DocumentId(asked)) : Optional.empty();
    }
}

package com.dokiments.promo.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A promotion and the code that redeems it. The codes are part of the product, as in the web app: the code is shown on
 * the sign-in page, so it is no secret, and what matters is only that each account redeems it once.
 *
 * @param id the promotion's ID, which is also the document ID of a redemption, so an account can redeem it only once
 * @param code the code, as stored with a redemption whatever the user typed
 * @param label the promotion's name
 * @param description what the code does
 */
public record PromoCode(String id, String code, String label, String description) {

    /** The longest code that is looked up, in characters. */
    public static final int MAX_LENGTH = 64;

    /** The promotions that exist. */
    static final List<PromoCode> ALL = List.of(new PromoCode(
            "launch-2026",
            "ViSHDOK2026!",
            "Launch Promo",
            "Use this code to unlock our launch offer on your account."));

    /**
     * Spaces anywhere in a code, as JavaScript's {@code \s} finds them, including non-breaking spaces and the byte
     * order mark, which a pasted code can carry.
     */
    private static final Pattern SPACE =
            Pattern.compile("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]");

    /**
     * The promotion a typed code redeems. Matching ignores case and every space, as the web app's does, because the
     * code is copied from a page and typed on phones that capitalise and insert spaces.
     */
    public static Optional<PromoCode> find(String typed) {
        String normalized = normalize(typed);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return ALL.stream()
                .filter(promo -> normalize(promo.code()).equals(normalized))
                .findFirst();
    }

    /** Whether nothing but spaces was typed, which counts as no code at all. */
    public static boolean isBlank(String typed) {
        return normalize(typed).isEmpty();
    }

    static String normalize(String value) {
        return SPACE.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
    }
}

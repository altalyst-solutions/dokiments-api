package com.dokiments.sharing.domain;

import com.dokiments.shared.EmailAddresses;
import com.dokiments.shared.TypedText;
import java.util.Locale;

/** Addresses as sharing compares and keeps them: trimmed and lower-case, as the web app does. */
public final class ShareEmails {

    private ShareEmails() {}

    /** The address as compared and kept. */
    public static String normalize(String email) {
        return TypedText.trim(email).toLowerCase(Locale.ROOT);
    }

    /**
     * An address shown in part, as the web app masks it: its first character, then {@code ***@} and its domain, such as
     * {@code j***@example.com}.
     */
    public static String mask(String email) {
        String[] parts = email.split("@", -1);
        String local = parts[0];
        String domain = parts.length > 1 ? parts[1] : "";
        return (local.isEmpty() ? "" : local.substring(0, local.offsetByCodePoints(0, 1))) + "***@" + domain;
    }

    /**
     * An address typed to share with: trimmed, at most 254 characters and plausible, then made lower-case.
     *
     * @throws InvalidShareException if it isn't a plausible address
     */
    public static String typed(String email) {
        String trimmed = TypedText.trim(email);
        if (EmailAddresses.isImplausible(trimmed)) {
            throw new InvalidShareException();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}

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

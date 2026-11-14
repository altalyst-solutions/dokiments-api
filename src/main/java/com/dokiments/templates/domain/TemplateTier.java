package com.dokiments.templates.domain;

import java.util.Locale;
import java.util.Optional;

/** Which plans include a template or style. Every plan includes the tiers below it. */
public enum TemplateTier {
    FREE,
    SILVER,
    GOLD;

    /** The stored and API form, for example {@code silver}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The tier a stored value names, or {@link #FREE} for anything else, as the web app reads it. */
    public static TemplateTier fromStored(String value) {
        return parse(value).orElse(FREE);
    }

    /** The tier exactly as named, for a filter that must name one of them. */
    public static Optional<TemplateTier> parse(String value) {
        for (TemplateTier tier : values()) {
            if (tier.value().equals(value)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}

package com.dokiments.platform.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyKeysTest {

    @Test
    void keysMatchTheWebAppsSoAnEmailIsNeverDeliveredTwiceDuringTheSwitchOver() {
        // The same key the web app builds with hashedIdempotencyKey("signed", ...) for these parts.
        assertThat(IdempotencyKeys.hashed("signed", "shared-1", "uid-2", 1793655000000L))
                .isEqualTo("signed-88f62769f078207962da22c9742f91aea5e43db74cdab57504894f193a51057f");
    }

    @Test
    void keysStayWithinResendsLimitWhateverTheParts() {
        String longAddress = "a".repeat(240) + "@example.com";

        assertThat(IdempotencyKeys.hashed("share", "shared-1", longAddress, 1L)).hasSize("share-".length() + 64);
    }
}

package com.dokiments.newsletter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.platform.web.ErrorMessages;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Which addresses can subscribe, matching the web app's rule. */
class SubscriberEmailTest {

    @Test
    void anAddressIsTrimmedAndLowerCased() {
        assertThat(SubscriberEmail.of("  Jane.Doe@Example.COM \t").value()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void nonBreakingSpacesAroundAPastedAddressAreRemovedToo() {
        assertThat(SubscriberEmail.of("\u00A0jane@example.com\uFEFF").value()).isEqualTo("jane@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane@example.com", "j.o'neil+news@mail.example.co.uk", "a_b-c@sub-domain.example.org"})
    void plausibleAddressesAreAccepted(String address) {
        assertThat(SubscriberEmail.of(address).value()).isEqualTo(address.toLowerCase(Locale.ROOT));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "jane",
                "jane@",
                "@example.com",
                "jane@example",
                "jane@example.c",
                "jane doe@example.com",
                "jane..doe@example.com",
                ".jane@example.com",
                "jane.@example.com",
                "jane@-example.com",
                "jäne@example.com"
            })
    void implausibleAddressesAreRefused(String address) {
        assertThatThrownBy(() -> SubscriberEmail.of(address)).isInstanceOf(InvalidSubscriberEmailException.class);
    }

    @Test
    void addressesLongerThan254CharactersAreRefused() {
        String atLimit = "a".repeat(254 - "@example.com".length()) + "@example.com";

        assertThat(SubscriberEmail.of(atLimit).value()).hasSize(254);
        assertThatThrownBy(() -> SubscriberEmail.of("a" + atLimit)).isInstanceOf(InvalidSubscriberEmailException.class);
    }

    @Test
    void theRefusalUsesTheWebAppsWording() {
        assertThat(new InvalidSubscriberEmailException().publicMessage()).isEqualTo(ErrorMessages.INVALID_BODY);
    }
}

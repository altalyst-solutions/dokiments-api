package com.dokiments.clients.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClientDetailsTest {

    @Test
    void everyFieldIsTrimmedAsTheWebAppTrimsIt() {
        var details = ClientDetails.of(
                " 1 Main St ", " C123 ", " Acme ", " Jane@Acme.example ", "\uFEFFJane\u00A0", " ID1 ", " 555 ");

        assertThat(details)
                .isEqualTo(new ClientDetails("1 Main St", "C123", "Acme", "Jane@Acme.example", "Jane", "ID1", "555"));
    }

    @Test
    void theOptionalFieldsMayBeEmpty() {
        assertThat(ClientDetails.of("", "", "Acme", "jane@acme.example", "Jane", "  ", "555")
                        .nationalId())
                .isEmpty();
    }

    @Test
    void theNameCompanyNameAndPhoneAreRequired() {
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "jane@acme.example", "  ", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "", "jane@acme.example", "Jane", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "jane@acme.example", "Jane", "", " "));
    }

    @Test
    void theEmailMustBePlausible() {
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "", "Jane", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "jane@acme", "Jane", "", "555"));
    }

    @Test
    void eachFieldHasTheWebAppsLimitAfterTrimming() {
        String name200 = "n".repeat(200);
        assertThat(ClientDetails.of(
                        "a".repeat(500), "b".repeat(100), name200, "j@a.co", name200, "i".repeat(100), "5".repeat(50)))
                .isNotNull();
        assertThat(ClientDetails.of("", "", " " + name200 + " ", "j@a.co", "Jane", "", "555")
                        .companyName())
                .hasSize(200);

        assertInvalid(() -> ClientDetails.of("a".repeat(501), "", "Acme", "j@a.co", "Jane", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "b".repeat(101), "Acme", "j@a.co", "Jane", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "c".repeat(201), "j@a.co", "Jane", "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "j@a.co", "n".repeat(201), "", "555"));
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "j@a.co", "Jane", "i".repeat(101), "555"));
        assertInvalid(() -> ClientDetails.of("", "", "Acme", "j@a.co", "Jane", "", "5".repeat(51)));
    }

    static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidClientException.class).hasMessage("Invalid request body.");
    }
}

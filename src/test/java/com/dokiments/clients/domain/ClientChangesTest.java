package com.dokiments.clients.domain;

import static com.dokiments.clients.domain.ClientDetailsTest.assertInvalid;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientChangesTest {

    private static final ClientDetails JANE =
            new ClientDetails("1 Main St", "C123", "Acme", "jane@acme.example", "Jane", "ID1", "555");

    @Test
    void onlyTheGivenFieldsChange() {
        var changes = ClientChanges.of(null, null, null, null, null, null, " 777 ");

        assertThat(changes.applyTo(JANE))
                .isEqualTo(new ClientDetails("1 Main St", "C123", "Acme", "jane@acme.example", "Jane", "ID1", "777"));
    }

    @Test
    void anOptionalFieldCanBeCleared() {
        assertThat(ClientChanges.of("", null, null, null, null, null, null)
                        .applyTo(JANE)
                        .address())
                .isEmpty();
    }

    @Test
    void aGivenFieldFollowsTheRulesForANewClient() {
        assertInvalid(() -> ClientChanges.of(null, null, null, null, " ", null, null));
        assertInvalid(() -> ClientChanges.of(null, null, null, "nope", null, null, null));
        assertInvalid(() -> ClientChanges.of(null, null, null, null, null, null, "5".repeat(51)));
    }

    @Test
    void atLeastOneFieldIsNeeded() {
        assertInvalid(() -> ClientChanges.of(null, null, null, null, null, null, null));
    }
}

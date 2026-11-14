package com.dokiments.clients.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClientIdTest {

    @Test
    void aNewIdHasTheWebAppsForm() {
        var id = ClientId.generate(Instant.ofEpochMilli(1_763_000_000_000L), new Random(1));

        assertThat(id.value()).matches("client_" + Long.toString(1_763_000_000_000L, 36) + "_[0-9a-z]{6}");
    }

    @Test
    void anIdFromAUrlIsAcceptedOnlyWhenFirestoreCouldStoreIt() {
        assertThat(ClientId.parse("client_mhx2k1q8_4f9a2c")).contains(new ClientId("client_mhx2k1q8_4f9a2c"));
        assertThat(ClientId.parse("any old id")).isPresent();

        assertThat(ClientId.parse("")).isEmpty();
        assertThat(ClientId.parse("a/b")).isEmpty();
        assertThat(ClientId.parse(".")).isEmpty();
        assertThat(ClientId.parse("..")).isEmpty();
        assertThat(ClientId.parse("__name__")).isEmpty();
        assertThat(ClientId.parse("x".repeat(1501))).isEmpty();
        assertThat(ClientId.parse("é".repeat(751))).isEmpty();
    }
}

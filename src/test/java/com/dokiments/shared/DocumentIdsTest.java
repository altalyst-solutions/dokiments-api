package com.dokiments.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentIdsTest {

    @Test
    void anIdIsStorableUnlessFirestoreWouldRefuseIt() {
        assertThat(DocumentIds.isStorable("classic-invoice")).isTrue();
        assertThat(DocumentIds.isStorable("_x_")).isTrue();
        assertThat(DocumentIds.isStorable("x".repeat(1500))).isTrue();

        assertThat(DocumentIds.isStorable("")).isFalse();
        assertThat(DocumentIds.isStorable("a/b")).isFalse();
        assertThat(DocumentIds.isStorable(".")).isFalse();
        assertThat(DocumentIds.isStorable("..")).isFalse();
        assertThat(DocumentIds.isStorable("__name__")).isFalse();
        assertThat(DocumentIds.isStorable("x".repeat(1501))).isFalse();
        assertThat(DocumentIds.isStorable("é".repeat(751))).isFalse();
    }
}

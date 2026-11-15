package com.dokiments.promo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromoCodeTest {

    @Test
    void aCodeMatchesWhateverItsCaseAndSpaces() {
        for (String typed : new String[] {
            "ViSHDOK2026!", "vishdok2026!", " VISH DOK 2026! ", "vish\u00A0dok2026!\uFEFF", "V\ti\ns" + "hdok2026!"
        }) {
            assertThat(PromoCode.find(typed)).as(typed).map(PromoCode::id).contains("launch-2026");
        }
    }

    @Test
    void anythingElseMatchesNothing() {
        assertThat(PromoCode.find("VISHDOK2025!")).isEmpty();
        assertThat(PromoCode.find("VISHDOK2026")).isEmpty();
        assertThat(PromoCode.find("   ")).isEmpty();
        assertThat(PromoCode.find("")).isEmpty();
    }

    @Test
    void onlySpacesCountAsNoCode() {
        assertThat(PromoCode.isBlank(" \u00A0 ")).isTrue();
        assertThat(PromoCode.isBlank("")).isTrue();
        assertThat(PromoCode.isBlank("x")).isFalse();
    }
}

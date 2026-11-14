package com.dokiments.templates.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.shared.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

class CatalogFilterTest {

    @Test
    void theStyleAndTierMustBeOnesTheWebAppKnows() {
        assertThat(CatalogFilter.of("brutalist", "gold")).isEqualTo(new CatalogFilter("brutalist", TemplateTier.GOLD));
        assertThat(CatalogFilter.of(null, null)).isEqualTo(new CatalogFilter(null, null));

        for (var bad :
                new String[][] {{"retro", null}, {"", null}, {"Classic", null}, {null, "platinum"}, {null, ""}}) {
            assertThatThrownBy(() -> CatalogFilter.of(bad[0], bad[1]))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Invalid query parameters.");
        }
    }

    @Test
    void anUnknownStoredTierReadsAsFree() {
        assertThat(TemplateTier.fromStored("gold")).isEqualTo(TemplateTier.GOLD);
        assertThat(TemplateTier.fromStored("platinum")).isEqualTo(TemplateTier.FREE);
        assertThat(TemplateTier.fromStored("")).isEqualTo(TemplateTier.FREE);
    }
}

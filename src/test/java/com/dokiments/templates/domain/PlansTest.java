package com.dokiments.templates.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.users.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlansTest {

    @Test
    void eachPlanIncludesTheTiersUpToItsOwn() {
        assertThat(Plans.includes(UserRole.FREE, TemplateTier.FREE, true)).isTrue();
        assertThat(Plans.includes(UserRole.FREE, TemplateTier.SILVER, true)).isFalse();
        assertThat(Plans.includes(UserRole.SILVER, TemplateTier.SILVER, true)).isTrue();
        assertThat(Plans.includes(UserRole.SILVER, TemplateTier.GOLD, true)).isFalse();
        assertThat(Plans.includes(UserRole.GOLD, TemplateTier.GOLD, true)).isTrue();
        assertThat(Plans.includes(UserRole.SPECIAL, TemplateTier.GOLD, true)).isTrue();
        assertThat(Plans.includes(UserRole.SUPERADMIN, TemplateTier.GOLD, true)).isTrue();
    }

    @Test
    void withoutTierLocksEveryPlanIncludesEveryTier() {
        assertThat(Plans.includes(UserRole.FREE, TemplateTier.GOLD, false)).isTrue();
    }

    @Test
    void onlyAFreeAccountHasASavedTemplateLimit() {
        assertThat(Plans.savedTemplateLimit(UserRole.FREE)).isEqualTo(2);
        assertThat(Plans.savedTemplateLimit(UserRole.SILVER)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Plans.savedTemplateLimit(UserRole.SUPERADMIN)).isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest(name = "{0} saved, already saved {1}, limit {2}: may save {3}")
    @CsvSource({"1, false, 2, true", "2, false, 2, false", "2, true, 2, true", "5, true, 2, true"})
    void aTemplateSavedAlreadyCanAlwaysBeSavedAgain(int savedCount, boolean alreadySaved, int limit, boolean maySave) {
        assertThat(Plans.maySave(savedCount, alreadySaved, limit)).isEqualTo(maySave);
    }
}

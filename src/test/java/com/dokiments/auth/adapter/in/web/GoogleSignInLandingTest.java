package com.dokiments.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.auth.domain.ReturnPath;
import com.dokiments.promo.PromoOutcome;
import org.junit.jupiter.api.Test;

class GoogleSignInLandingTest {

    @Test
    void thePromoOutcomeIsAddedToTheLandingPageKeepingItsQuery() {
        assertThat(GoogleSignInController.landing(new ReturnPath("/dashboard"), null))
                .hasToString("/dashboard");
        assertThat(GoogleSignInController.landing(new ReturnPath("/dashboard"), PromoOutcome.APPLIED))
                .hasToString("/dashboard?promo=applied");
        assertThat(GoogleSignInController.landing(new ReturnPath("/my-documents?tab=shared"), PromoOutcome.INVALID))
                .hasToString("/my-documents?tab=shared&promo=invalid");
    }

    @Test
    void aPromoParameterInTheLinkIsReplacedSoItCantBeFaked() {
        assertThat(GoogleSignInController.landing(
                        new ReturnPath("/dashboard?promo=applied"), PromoOutcome.ALREADY_REDEEMED))
                .hasToString("/dashboard?promo=already_redeemed");
    }
}

package com.dokiments.promo.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.promo.PromoOutcome;
import com.dokiments.promo.application.port.out.PromoRedemptionStore;
import com.dokiments.promo.domain.InvalidPromoCodeException;
import com.dokiments.promo.domain.PromoAlreadyRedeemedException;
import com.dokiments.promo.domain.PromoRedemption;
import com.dokiments.shared.error.UpstreamUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class PromoServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T09:30:00.123456Z");

    private final Map<String, Map<String, PromoRedemption>> stored = new HashMap<>();
    private boolean storeDown;
    private final PromoRedemptionStore store = new PromoRedemptionStore() {
        @Override
        public List<PromoRedemption> all(String uid) {
            return new ArrayList<>(stored.getOrDefault(uid, Map.of()).values());
        }

        @Override
        public boolean add(String uid, PromoRedemption redemption) {
            if (storeDown) {
                throw new UpstreamUnavailableException(new IllegalStateException("Firestore is down"));
            }
            return stored.computeIfAbsent(uid, key -> new HashMap<>()).putIfAbsent(redemption.promoId(), redemption)
                    == null;
        }
    };
    private final PromoService service = new PromoService(store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aRedemptionKeepsThePromotionsOwnCodeAndTheTimeInMilliseconds() {
        var redemption = service.redeem("uid-1", " vishdok2026! ");

        assertThat(redemption)
                .isEqualTo(
                        new PromoRedemption("launch-2026", "ViSHDOK2026!", Instant.parse("2026-11-15T09:30:00.123Z")));
        assertThat(service.list("uid-1")).containsExactly(redemption);
        assertThat(service.list("uid-2")).isEmpty();
    }

    @Test
    void aPromotionIsRedeemedOncePerAccount() {
        service.redeem("uid-1", "ViSHDOK2026!");

        assertThatThrownBy(() -> service.redeem("uid-1", "ViSHDOK2026!"))
                .isInstanceOf(PromoAlreadyRedeemedException.class)
                .hasMessage("This promo code has already been used for your account.");
        assertThat(service.redeem("uid-2", "ViSHDOK2026!").promoId()).isEqualTo("launch-2026");
    }

    @Test
    void aWrongCodeIsRefused() {
        assertThatThrownBy(() -> service.redeem("uid-1", "   "))
                .isInstanceOf(InvalidPromoCodeException.class)
                .hasMessage("Invalid promo code.");
    }

    @Test
    void atSignInEveryOutcomeIsAnAnswerRatherThanAFailure() {
        assertThat(service.redeemAtSignIn("uid-1", null)).isEmpty();
        assertThat(service.redeemAtSignIn("uid-1", "  ")).isEmpty();
        assertThat(service.redeemAtSignIn("uid-1", "nope")).contains(PromoOutcome.INVALID);
        assertThat(service.redeemAtSignIn("uid-1", "ViSHDOK2026!")).contains(PromoOutcome.APPLIED);
        assertThat(service.redeemAtSignIn("uid-1", "ViSHDOK2026!")).contains(PromoOutcome.ALREADY_REDEEMED);

        storeDown = true;
        assertThat(service.redeemAtSignIn("uid-2", "ViSHDOK2026!")).contains(PromoOutcome.FAILED);
    }

    @Test
    void theOutcomesHaveTheWebAppsNames() {
        assertThat(PromoOutcome.ALREADY_REDEEMED.value()).isEqualTo("already_redeemed");
        assertThat(PromoOutcome.APPLIED.value()).isEqualTo("applied");
    }
}

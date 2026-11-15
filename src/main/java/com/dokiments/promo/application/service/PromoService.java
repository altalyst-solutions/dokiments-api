package com.dokiments.promo.application.service;

import com.dokiments.promo.PromoAtSignIn;
import com.dokiments.promo.PromoOutcome;
import com.dokiments.promo.application.port.in.ManagePromoRedemptions;
import com.dokiments.promo.application.port.out.PromoRedemptionStore;
import com.dokiments.promo.domain.InvalidPromoCodeException;
import com.dokiments.promo.domain.PromoAlreadyRedeemedException;
import com.dokiments.promo.domain.PromoCode;
import com.dokiments.promo.domain.PromoRedemption;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class PromoService implements ManagePromoRedemptions, PromoAtSignIn {

    private static final Logger log = LoggerFactory.getLogger(PromoService.class);

    private final PromoRedemptionStore store;
    private final Clock clock;

    PromoService(PromoRedemptionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public List<PromoRedemption> list(String uid) {
        return store.all(uid);
    }

    @Override
    public PromoRedemption redeem(String uid, String promoCode) {
        var promo = PromoCode.find(promoCode).orElseThrow(InvalidPromoCodeException::new);
        // Milliseconds, the precision the web app stores and answers with.
        var redemption =
                new PromoRedemption(promo.id(), promo.code(), clock.instant().truncatedTo(ChronoUnit.MILLIS));
        if (!store.add(uid, redemption)) {
            throw new PromoAlreadyRedeemedException();
        }
        return redemption;
    }

    @Override
    public Optional<PromoOutcome> redeemAtSignIn(String uid, @Nullable String promoCode) {
        if (promoCode == null || PromoCode.isBlank(promoCode)) {
            return Optional.empty();
        }
        try {
            redeem(uid, promoCode);
            return Optional.of(PromoOutcome.APPLIED);
        } catch (InvalidPromoCodeException e) {
            return Optional.of(PromoOutcome.INVALID);
        } catch (PromoAlreadyRedeemedException e) {
            return Optional.of(PromoOutcome.ALREADY_REDEEMED);
        } catch (RuntimeException e) {
            // The account is signed in by now, so a failed redemption is reported for the user to try again from the
            // settings rather than failing the sign-in.
            log.warn("A promo code given while signing in couldn't be redeemed", e);
            return Optional.of(PromoOutcome.FAILED);
        }
    }
}

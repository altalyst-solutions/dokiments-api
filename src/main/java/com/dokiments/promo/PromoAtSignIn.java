package com.dokiments.promo;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Redeems a promo code given alongside signing up or in. Only call it once the account is proven (the credentials
 * accepted, the account created, or Google's answer checked), so a code can never be redeemed for an account the caller
 * doesn't own.
 */
public interface PromoAtSignIn {

    /**
     * Redeems the code for the account. Never throws: a promo problem must never fail the sign-in it came with, so
     * every failure is an outcome to show.
     *
     * @param promoCode the code as typed; empty text or spaces count as no code, because the form always has the field,
     *     also for users without a code
     * @return empty when no code was given
     */
    Optional<PromoOutcome> redeemAtSignIn(String uid, @Nullable String promoCode);
}

package com.dokiments.promo.domain;

import java.time.Instant;

/**
 * An account's redemption of a promotion.
 *
 * @param promoId the promotion's ID
 * @param code the promotion's code, as the promotion has it rather than as typed
 * @param redeemedAt when it was redeemed
 */
public record PromoRedemption(String promoId, String code, Instant redeemedAt) {}

package com.dokiments.promo.application.port.in;

import com.dokiments.promo.domain.PromoRedemption;
import java.util.List;

/** The promo codes an account has redeemed. Every method works only on the account {@code uid}, the signed-in user. */
public interface ManagePromoRedemptions {

    /**
     * Every redemption of the account, in the order they are stored, by promotion. A promotion that has ended is still
     * listed: it is still true that the account redeemed it.
     */
    List<PromoRedemption> list(String uid);

    /**
     * Redeems a code for the account.
     *
     * @throws com.dokiments.promo.domain.InvalidPromoCodeException if the code matches no promotion
     * @throws com.dokiments.promo.domain.PromoAlreadyRedeemedException if the account redeemed it already
     */
    PromoRedemption redeem(String uid, String promoCode);
}

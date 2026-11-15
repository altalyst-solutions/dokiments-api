package com.dokiments.promo.application.port.out;

import com.dokiments.promo.domain.PromoRedemption;
import java.util.List;

/** Where each account's redemptions are kept, one per promotion. */
public interface PromoRedemptionStore {

    /** All of the account's redemptions, in the order they are stored. */
    List<PromoRedemption> all(String uid);

    /**
     * Stores a redemption, unless the account has one for this promotion already. Checked and written as one step, so
     * two redemptions at the same moment can't both succeed.
     *
     * @return {@code false} if the account had redeemed the promotion already, in which case nothing changes
     */
    boolean add(String uid, PromoRedemption redemption);
}

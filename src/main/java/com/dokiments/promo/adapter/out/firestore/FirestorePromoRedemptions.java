package com.dokiments.promo.adapter.out.firestore;

import com.dokiments.platform.firestore.FirestoreCallException;
import com.dokiments.platform.firestore.FirestoreCalls;
import com.dokiments.promo.application.port.out.PromoRedemptionStore;
import com.dokiments.promo.domain.PromoRedemption;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Each account's redemptions in {@code users/{uid}/promoRedemptions/{promoId}}, as the web app keeps them: one document
 * per promotion, named by its ID, holding {@code code} and {@code redeemedAt}. Reading is lenient: a missing code reads
 * as empty text and a missing time as {@code 0}.
 */
@Repository
class FirestorePromoRedemptions implements PromoRedemptionStore {

    private final Firestore firestore;

    FirestorePromoRedemptions(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<PromoRedemption> all(String uid) {
        return FirestoreCalls.await(redemptions(uid).get()).getDocuments().stream()
                .map(FirestorePromoRedemptions::redemption)
                .toList();
    }

    @Override
    public boolean add(String uid, PromoRedemption redemption) {
        try {
            // create() fails when the document exists, so a promotion is redeemed once per account even when two
            // redemptions arrive together: Firestore lets only one of them write.
            FirestoreCalls.await(redemptions(uid)
                    .document(redemption.promoId())
                    .create(Map.of(
                            "code", redemption.code(),
                            "redeemedAt", FirestoreCalls.toTimestamp(redemption.redeemedAt()))));
            return true;
        } catch (FirestoreCallException e) {
            if (e.code() == StatusCode.Code.ALREADY_EXISTS) {
                return false;
            }
            throw e;
        }
    }

    private CollectionReference redemptions(String uid) {
        return firestore.collection("users").document(uid).collection("promoRedemptions");
    }

    private static PromoRedemption redemption(DocumentSnapshot document) {
        return new PromoRedemption(
                document.getId(),
                document.get("code") instanceof String code ? code : "",
                document.get("redeemedAt") instanceof Timestamp time ? FirestoreCalls.toInstant(time) : Instant.EPOCH);
    }
}

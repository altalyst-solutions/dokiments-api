package com.dokiments.auth.application.port.out;

import com.dokiments.auth.domain.ReturnPath;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Google sign-ins that have started and wait for Google's answer, by their {@code state}: the random value that ties
 * the callback to the start, so another site can't complete a sign-in it didn't start.
 */
public interface PendingSignIns {

    /** How long someone has to get through Google's pages. */
    Duration LIFETIME = Duration.ofMinutes(10);

    void save(String state, PendingSignIn signIn);

    /** The sign-in, removed as it is taken, so each {@code state} can complete one sign-in at most. */
    Optional<PendingSignIn> take(String state);

    /**
     * What a started sign-in carries to its end.
     *
     * @param returnPath where to send the browser afterwards
     * @param promoCode a promo code given on the sign-in page, redeemed once the sign-in completes; {@code null} when
     *     none was given
     */
    record PendingSignIn(ReturnPath returnPath, @Nullable String promoCode) {}
}

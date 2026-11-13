package com.dokiments.auth.application.port.out;

import com.dokiments.auth.domain.ReturnPath;
import java.time.Duration;
import java.util.Optional;

/**
 * Google sign-ins that have started and wait for Google's answer, by their {@code state}: the random value that ties
 * the callback to the start, so another site can't complete a sign-in it didn't start.
 */
public interface PendingSignIns {

    /** How long someone has to get through Google's pages. */
    Duration LIFETIME = Duration.ofMinutes(10);

    void save(String state, ReturnPath returnPath);

    /** The sign-in's return path, removed as it is taken, so each {@code state} can complete one sign-in at most. */
    Optional<ReturnPath> take(String state);
}

package com.dokiments.platform.firestore;

import com.google.api.gax.rpc.StatusCode;
import java.io.Serial;

/**
 * A Firestore call that Firestore rejected, with the reason as a status code.
 *
 * <p>Adapters catch it for the cases that are an expected outcome and turn those into the right application exception,
 * for example {@link StatusCode.Code#ALREADY_EXISTS} into a {@code ConflictException}. Anything not handled is a bug or
 * an unexpected failure and ends up as a {@code 500}, logged with the original error as the cause.
 *
 * <p>Firestore being unreachable is not reported this way: {@link FirestoreCalls} turns that into an
 * {@code UpstreamUnavailableException} straight away, because no adapter can do anything else with it.
 */
public class FirestoreCallException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final StatusCode.Code code;

    FirestoreCallException(StatusCode.Code code, Throwable cause) {
        super("Firestore call failed with " + code, cause);
        this.code = code;
    }

    /** Why Firestore rejected the call, for example {@code ALREADY_EXISTS} or {@code FAILED_PRECONDITION}. */
    public StatusCode.Code code() {
        return code;
    }
}

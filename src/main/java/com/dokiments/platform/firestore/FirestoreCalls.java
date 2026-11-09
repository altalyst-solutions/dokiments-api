package com.dokiments.platform.firestore;

import com.dokiments.shared.error.UpstreamUnavailableException;
import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;

/**
 * Helpers shared by every Firestore adapter.
 *
 * <p>The Firestore client is asynchronous: each call returns an {@link ApiFuture}. The application runs on virtual
 * threads, where simply waiting for the result is cheap, so adapters call {@link #await(ApiFuture)} and get either the
 * result or one of two kinds of failure:
 *
 * <ul>
 *   <li>Firestore unreachable or overloaded: {@link UpstreamUnavailableException}, answered with {@code 503} so the
 *       client knows to try again.
 *   <li>Firestore rejected the call: {@link FirestoreCallException}, with the reason, for the adapter to handle.
 * </ul>
 */
public final class FirestoreCalls {

    /**
     * Status codes that mean "temporarily unable", not "rejected". The Firestore client already retries these with
     * back-off, so reaching this point means retrying didn't help.
     */
    private static final Set<StatusCode.Code> UNAVAILABLE = EnumSet.of(
            StatusCode.Code.UNAVAILABLE, StatusCode.Code.DEADLINE_EXCEEDED, StatusCode.Code.RESOURCE_EXHAUSTED);

    private FirestoreCalls() {}

    /**
     * Waits for a Firestore call and returns its result.
     *
     * @throws UpstreamUnavailableException if Firestore couldn't be reached, or the wait was interrupted
     * @throws FirestoreCallException if Firestore rejected the call
     */
    public static <T> T await(ApiFuture<T> call) {
        try {
            return call.get();
        } catch (ExecutionException e) {
            throw translate(e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            // Keep the interrupt for whoever stops this thread (usually a shutdown), and report it as unavailable.
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException(e);
        }
    }

    /** Turns a failure from the Firestore client into one of the two exceptions described above. */
    static RuntimeException translate(Throwable failure) {
        StatusCode.@Nullable Code code = statusCode(failure);
        if (code == null || UNAVAILABLE.contains(code)) {
            // No status at all means the call never got an answer (for example the connection broke).
            return new UpstreamUnavailableException(failure);
        }
        return new FirestoreCallException(code, failure);
    }

    /** Finds the gRPC status in the chain of causes. The client wraps it differently depending on the call. */
    private static StatusCode.@Nullable Code statusCode(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                return api.getStatusCode().getCode();
            }
        }
        return null;
    }

    /**
     * Converts an {@link Instant} to a Firestore {@link Timestamp}.
     *
     * <p>Firestore stores time with microsecond precision while {@code Instant} has nanoseconds, so the value is
     * truncated to microseconds here, explicitly. Otherwise, a value written and read back would differ from the
     * original in its last digits, and comparing them would fail for no visible reason.
     */
    public static Timestamp toTimestamp(Instant instant) {
        Instant micros = instant.truncatedTo(ChronoUnit.MICROS);
        return Timestamp.ofTimeSecondsAndNanos(micros.getEpochSecond(), micros.getNano());
    }

    /** Converts a Firestore {@link Timestamp} to an {@link Instant}. */
    public static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

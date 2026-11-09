package com.dokiments.platform.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.shared.error.UpstreamUnavailableException;
import com.google.api.core.ApiFutures;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import io.grpc.Status;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** How Firestore results and failures reach the adapters. */
class FirestoreCallsTest {

    @Test
    void aSuccessfulCallReturnsItsResult() {
        assertThat(FirestoreCalls.await(ApiFutures.immediateFuture("result"))).isEqualTo("result");
    }

    @ParameterizedTest
    @EnumSource(
            value = Status.Code.class,
            names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED"})
    void firestoreBeingUnreachableOrOverloadedIsReportedAsUnavailable(Status.Code code) {
        assertThatThrownBy(() -> FirestoreCalls.await(ApiFutures.immediateFailedFuture(apiException(code))))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void aFailureWithoutAnyAnswerIsReportedAsUnavailable() {
        assertThatThrownBy(() -> FirestoreCalls.await(
                        ApiFutures.immediateFailedFuture(new java.io.IOException("connection reset"))))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = Status.Code.class,
            names = {"ALREADY_EXISTS", "NOT_FOUND", "FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT"})
    void aRejectionKeepsItsReasonForTheAdapter(Status.Code code) {
        assertThatThrownBy(() -> FirestoreCalls.await(ApiFutures.immediateFailedFuture(apiException(code))))
                .isInstanceOfSatisfying(
                        FirestoreCallException.class,
                        e -> assertThat(e.code().name()).isEqualTo(code.name()));
    }

    @Test
    void theReasonIsFoundEvenWhenTheClientWrapsTheError() {
        var wrapped = new RuntimeException("transaction failed", apiException(Status.Code.ALREADY_EXISTS));

        assertThatThrownBy(() -> FirestoreCalls.await(ApiFutures.immediateFailedFuture(wrapped)))
                .isInstanceOfSatisfying(
                        FirestoreCallException.class,
                        e -> assertThat(e.code()).isEqualTo(StatusCode.Code.ALREADY_EXISTS));
    }

    @Test
    void anInterruptedWaitKeepsTheInterruptAndIsReportedAsUnavailable() {
        Thread.currentThread().interrupt();
        boolean stillInterrupted;
        try {
            // An unfinished future, so waiting for it hits the interrupt straight away.
            assertThatThrownBy(() -> FirestoreCalls.await(com.google.api.core.SettableApiFuture.create()))
                    .isInstanceOf(UpstreamUnavailableException.class);
        } finally {
            // Reads and clears the flag, so it can't leak into other tests on this thread.
            stillInterrupted = Thread.interrupted();
        }
        assertThat(stillInterrupted)
                .as("the interrupt must be kept for the caller")
                .isTrue();
    }

    @Test
    void timestampsAreTruncatedToTheMicrosecondsFirestoreStores() {
        var withNanos = Instant.parse("2026-07-18T10:15:30.123456789Z");

        var stored = FirestoreCalls.toTimestamp(withNanos);

        assertThat(FirestoreCalls.toInstant(stored)).isEqualTo(Instant.parse("2026-07-18T10:15:30.123456Z"));
    }

    private static ApiException apiException(Status.Code code) {
        return ApiExceptionFactory.createException(new RuntimeException(code.name()), GrpcStatusCode.of(code), false);
    }
}

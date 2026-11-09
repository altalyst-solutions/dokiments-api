package com.dokiments.platform.email;

/**
 * What happened to an email. Sending never throws: the caller decides what a failed email means, and it must never fail
 * the action that caused it.
 */
public sealed interface EmailResult {

    /** Resend accepted the email. {@code id} is Resend's, for looking it up in their dashboard. */
    record Sent(String id) implements EmailResult {}

    /** The email wasn't sent, for {@code reason}. */
    record NotSent(Reason reason) implements EmailResult {}

    enum Reason {
        /** No Resend key here, so email is off. Usually nothing to do. */
        DISABLED,
        /** Recipients are restricted and this one isn't on the allow list. The safety net working. */
        BLOCKED,
        /** The address isn't a plausible email address. */
        INVALID_RECIPIENT,
        /**
         * Resend rejected the email or couldn't be reached. Already logged. Don't retry in a loop; a later attempt with
         * the same idempotency key is safe.
         */
        PROVIDER_ERROR
    }
}

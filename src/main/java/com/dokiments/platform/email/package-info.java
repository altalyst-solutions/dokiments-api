/**
 * Sending transactional email through Resend, and the templates every email is built from.
 *
 * <p>Features send email with {@link com.dokiments.platform.email.EmailSender}, from an outbound adapter called by a
 * listener reacting to a domain event, so the request that caused the email never waits for it. Nothing outside this
 * package knows about Resend.
 *
 * <p>Locally and in tests, email only ever reaches the addresses on an allow list, and without a Resend key nothing is
 * sent at all. See {@link com.dokiments.platform.email.EmailProperties}.
 */
@NullMarked
package com.dokiments.platform.email;

import org.jspecify.annotations.NullMarked;

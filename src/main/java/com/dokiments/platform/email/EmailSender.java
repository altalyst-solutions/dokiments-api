package com.dokiments.platform.email;

/**
 * Sends transactional email. The only way the application sends email.
 *
 * <p>Call it from a listener reacting to a domain event, not from the use case itself, so the request never waits for
 * the email and can't fail because of it.
 */
public interface EmailSender {

    /** Renders and sends {@code message}. Never throws; see {@link EmailResult} for what can happen. */
    EmailResult send(EmailMessage message);
}

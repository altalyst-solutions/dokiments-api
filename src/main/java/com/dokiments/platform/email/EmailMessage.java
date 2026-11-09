package com.dokiments.platform.email;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One email to send.
 *
 * @param to the single recipient
 * @param subject the subject line. Names in it should already be cleaned with {@link EmailLabels#clean}; line breaks
 *     are removed here in any case.
 * @param template the template, without extension, relative to {@code src/main/resources/email/}, for example
 *     {@code sharing/document-shared}. Both its {@code .html} and {@code .txt} versions are rendered.
 * @param variables the values the template uses. Escaped as text in the HTML version, so they can't add markup.
 * @param idempotencyKey makes retries safe: Resend delivers each key once. Build it with {@link IdempotencyKeys} from
 *     what makes this email unique (for example the share, the recipient and the time it was shared), never from the
 *     time it is sent.
 * @param replyTo where replies go, instead of the configured address
 */
public record EmailMessage(
        String to,
        String subject,
        String template,
        Map<String, Object> variables,
        @Nullable String idempotencyKey,
        @Nullable String replyTo) {

    public EmailMessage {
        to = to.strip();
        subject = EmailLabels.clean(subject, 200, "Dokiments");
        variables = Map.copyOf(variables);
    }

    /** An email without an idempotency key or its own reply-to address. */
    public static EmailMessage of(String to, String subject, String template, Map<String, Object> variables) {
        return new EmailMessage(to, subject, template, variables, null, null);
    }

    /** The same email, delivered at most once for {@code key}. */
    public EmailMessage withIdempotencyKey(String key) {
        return new EmailMessage(to, subject, template, variables, key, replyTo);
    }
}

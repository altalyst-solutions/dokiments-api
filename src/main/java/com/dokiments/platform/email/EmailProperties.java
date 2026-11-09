package com.dokiments.platform.email;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Email settings, under {@code dokiments.email}. Each one's environment variable is mapped in {@code application.yml}.
 *
 * <p>Who receives email depends on the settings, as {@link #mode()} describes:
 *
 * <ul>
 *   <li>Without a Resend key, nothing is sent ({@link EmailMode#DISABLED}). The application works normally.
 *   <li>With a key and an allow list, or where recipients are restricted ({@code dev}, tests), only the addresses on
 *       the list receive email ({@link EmailMode#RESTRICTED}). An empty list then means nobody does. A test can never
 *       email a real user.
 *   <li>With a key and neither, every recipient does ({@link EmailMode#LIVE}). Only production runs like this.
 * </ul>
 *
 * @param resendApiKey Resend's API key, a send-only key limited to the sending domain. Empty means email is off.
 * @param from the sender, as {@code Name <address>}. The address must be on the domain verified in Resend, or Resend
 *     rejects the email.
 * @param replyTo where replies go. The sending domain can't receive mail, so without it, replies bounce.
 * @param allowList the only addresses that receive email when recipients are restricted
 * @param restrictRecipients whether only the allow list receives email even when it is empty: set in {@code dev} and
 *     tests
 * @param siteUrl the web app's address, for the links in emails
 * @param resendUrl Resend's API address. Only changed by tests, to point at a local server.
 */
@ConfigurationProperties("dokiments.email")
public record EmailProperties(
        @DefaultValue("") String resendApiKey,
        @DefaultValue(DEFAULT_FROM) String from,
        @Nullable String replyTo,
        @DefaultValue List<String> allowList,
        @DefaultValue("false") boolean restrictRecipients,
        @DefaultValue("https://dokiments.com") URI siteUrl,
        @DefaultValue("https://api.resend.com") URI resendUrl) {

    /** The sender on the domain verified in Resend. */
    public static final String DEFAULT_FROM = "Dokiments <notifications@mail.dokiments.com>";

    public EmailProperties {
        resendApiKey = resendApiKey.strip();
        from = from.isBlank() ? DEFAULT_FROM : from.strip();
        replyTo = replyTo == null || replyTo.isBlank() ? null : replyTo.strip();
        // Lower case, so the check doesn't depend on how an address was typed. Empty entries come from a trailing
        // comma or an unset variable.
        allowList = allowList.stream()
                .map(address -> address.strip().toLowerCase(Locale.ROOT))
                .filter(address -> !address.isEmpty())
                .distinct()
                .toList();
        if (!siteUrl.isAbsolute()) {
            throw new IllegalArgumentException(
                    "dokiments.email.site-url must be an absolute address, such as https://dokiments.com (SITE_URL).");
        }
    }

    /** Who receives email with these settings. */
    public EmailMode mode() {
        if (resendApiKey.isEmpty()) {
            return EmailMode.DISABLED;
        }
        return restrictRecipients || !allowList.isEmpty() ? EmailMode.RESTRICTED : EmailMode.LIVE;
    }

    /** Whether {@code address} may receive email, as far as the allow list is concerned. */
    boolean allows(String address) {
        return mode() != EmailMode.RESTRICTED || allowList.contains(address.toLowerCase(Locale.ROOT));
    }
}

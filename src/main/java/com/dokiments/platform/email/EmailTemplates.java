package com.dokiments.platform.email;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders an email's HTML and plain-text versions from its templates in {@code src/main/resources/email/}.
 *
 * <p>Every email is sent with both: some mail clients and many spam filters only read the plain text. The two are
 * separate templates ({@code .html} and {@code .txt}) rather than one converted to the other, so each reads well.
 *
 * <p>Besides the email's own values, every template can use:
 *
 * <ul>
 *   <li>{@code subject}: the email's subject, which is also its preview text in the inbox;
 *   <li>{@code siteName}: "Dokiments";
 *   <li>{@code siteOrigin} and {@code siteHost}: the public web address, for the footer link;
 *   <li>{@code logoUrl}: the hosted logo image.
 * </ul>
 */
final class EmailTemplates {

    static final String SITE_NAME = "Dokiments";

    /**
     * Where the logo and footer link point when the configured site is local. The recipient's mail client can't reach
     * {@code localhost}, so a locally sent email would otherwise show a broken image.
     */
    static final URI PUBLIC_SITE = URI.create("https://www.dokiments.com");

    private static final Pattern LOCAL_HOST = Pattern.compile("localhost|127\\.0\\.0\\.1|\\[::1]");

    private final SpringTemplateEngine engine;
    private final URI publicOrigin;

    EmailTemplates(URI siteUrl) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("email/");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setHtmlTemplateModePatterns(Set.of("*.html"));
        resolver.setTextTemplateModePatterns(Set.of("*.txt"));
        // A missing template must fail loudly, not render as an empty email.
        resolver.setCheckExistence(true);
        this.engine = new SpringTemplateEngine();
        this.engine.setTemplateResolver(resolver);
        this.publicOrigin = isLocal(siteUrl) ? PUBLIC_SITE : origin(siteUrl);
    }

    /** Both versions of {@code message}'s email. */
    Rendered render(EmailMessage message) {
        Map<String, Object> variables = new HashMap<>(message.variables());
        variables.put("subject", message.subject());
        variables.put("siteName", SITE_NAME);
        variables.put("siteOrigin", publicOrigin.toString());
        variables.put("siteHost", publicOrigin.getHost());
        variables.put("logoUrl", publicOrigin.resolve("/icons/icon-192.png").toString());
        var context = new Context(null, variables);
        return new Rendered(
                engine.process(message.template() + ".html", context),
                engine.process(message.template() + ".txt", context).strip() + "\n");
    }

    record Rendered(String html, String text) {}

    private static boolean isLocal(URI url) {
        return url.getHost() == null || LOCAL_HOST.matcher(url.getHost()).matches();
    }

    private static URI origin(URI url) {
        String port = url.getPort() == -1 ? "" : ":" + url.getPort();
        return URI.create(url.getScheme() + "://" + url.getHost() + port);
    }
}

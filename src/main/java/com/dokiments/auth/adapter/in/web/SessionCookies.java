package com.dokiments.auth.adapter.in.web;

import com.dokiments.auth.domain.AuthSession;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie, as the web app's: {@code dokiments-session}, HttpOnly so JavaScript can't read it, Secure so it
 * only travels over HTTPS, and {@code SameSite=Lax} so other sites' requests don't carry it while the redirect back
 * from Google sign-in still does. It lasts as long as a session at most, one day.
 *
 * <p>Declared here rather than through {@code server.servlet.session.cookie.*}: Spring Session doesn't pick those up
 * with this setup, and the cookie is part of the contract with the web app, so it is spelled out in code.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookies {

    static final String NAME = "dokiments-session";

    @Bean
    CookieSerializer sessionCookieSerializer(Settings settings) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(NAME);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(settings.secureCookies());
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge((int) AuthSession.MAX_AGE.toSeconds());
        return serializer;
    }

    /**
     * @param secureCookies whether the session cookies are sent over HTTPS only. On, except where the API runs on plain
     *     {@code http://localhost} ({@code dev}, and the Compose container), where a Secure cookie would never be sent
     *     back.
     */
    @ConfigurationProperties("dokiments.auth")
    record Settings(@DefaultValue("true") boolean secureCookies) {}
}

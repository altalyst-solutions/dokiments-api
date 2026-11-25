package com.dokiments.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The application's own settings, under the {@code dokiments} prefix in {@code application.yml}.
 *
 * <p>Bound once at start-up into immutable records, so code reads a typed value instead of looking up strings, and a
 * setting that can't be converted (say {@code DOCUMENT_SHARING_ENABLED=yes please}) stops the application from starting
 * instead of being misread later. Each setting's environment variable is mapped in {@code application.yml}, where the
 * default is also set.
 *
 * @param features switches that turn whole features on or off
 * @param async how {@code @Async} listeners run
 */
@ConfigurationProperties("dokiments")
public record DokimentsProperties(
        @DefaultValue Features features, @DefaultValue Async async) {

    /**
     * Feature switches. They match the web app's flags of the same purpose, so both sides can be switched together.
     *
     * @param documentSharing whether documents can be shared for signature. While off, the sharing endpoints answer
     *     {@code 404}, as if they didn't exist.
     * @param tierLocks whether plans limit which templates may be used. While off, every plan includes every template.
     *     The limit on how many templates a free account may save applies either way, as with the web app's flag.
     */
    public record Features(
            @DefaultValue("false") boolean documentSharing,
            @DefaultValue("true") boolean tierLocks) {}

    /**
     * How {@code @Async} listeners run (see {@code platform.async.AsyncConfiguration}).
     *
     * @param inline whether they run on the caller's thread, before the request answers, instead of on a thread of
     *     their own. For platforms that only give an instance CPU while it answers a request.
     */
    public record Async(@DefaultValue("false") boolean inline) {}
}

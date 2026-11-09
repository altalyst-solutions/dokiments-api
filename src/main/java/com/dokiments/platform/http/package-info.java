/**
 * How the application calls external services over HTTP (Firebase Auth, Google sign-in, Resend): timeouts, retries and
 * what counts as the service being unavailable.
 *
 * <p>Nothing here needs to be used directly. Every {@link org.springframework.web.client.RestClient} built from the
 * {@link org.springframework.web.client.RestClient.Builder} that Spring Boot provides gets this behaviour, so an
 * adapter injects the builder, sets the service's base URL and builds its client. See {@link HttpClientConfiguration}
 * for exactly what is applied.
 */
@NullMarked
package com.dokiments.platform.http;

import org.jspecify.annotations.NullMarked;

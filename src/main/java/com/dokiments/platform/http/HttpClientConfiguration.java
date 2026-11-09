package com.dokiments.platform.http;

import com.dokiments.shared.error.UpstreamUnavailableException;
import java.net.http.HttpClient;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Applies the rules for calling external services to every {@link org.springframework.web.client.RestClient} built from
 * Spring Boot's {@link org.springframework.web.client.RestClient.Builder}:
 *
 * <ul>
 *   <li>timeouts for connecting, for each attempt and for the whole call ({@link HttpClientProperties});
 *   <li>retries only where sending the request again is certainly safe ({@link UpstreamRetryInterceptor});
 *   <li>a service that can't be reached, is failing ({@code 5xx}) or is overloaded ({@code 429}) becomes
 *       {@link UpstreamUnavailableException}, answered with {@code 503}.
 * </ul>
 *
 * <p>Other error responses ({@code 4xx}) are left to the adapter, which knows what they mean for that service: for
 * example, Firebase answering {@code 400 INVALID_LOGIN_CREDENTIALS}. Handle them with {@code onStatus(...)}. One that
 * isn't handled is a bug and is answered with {@code 500}.
 */
@Configuration(proxyBeanMethods = false)
class HttpClientConfiguration {

    /**
     * One client for every external service, so connections are shared. Closed when the application stops.
     *
     * <p>Redirects aren't followed: none of these services redirect API calls, and following one could send the
     * request, with its credentials, to another address.
     */
    @Bean
    HttpClient upstreamHttpClient(HttpClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    RestClientCustomizer upstreamRestClientCustomizer(HttpClient upstreamHttpClient, HttpClientProperties properties) {
        return builder -> builder.requestFactory(new UpstreamRequestFactory(upstreamHttpClient, properties))
                .requestInterceptor(new UpstreamRetryInterceptor(properties))
                .defaultStatusHandler(HttpClientConfiguration::meansUnavailable, (request, response) -> {
                    throw new UpstreamUnavailableException(
                            new UpstreamErrorResponseException(request, response.getStatusCode()));
                });
    }

    /** The service is failing or asks us to slow down: either way, trying again later is the answer. */
    static boolean meansUnavailable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }
}

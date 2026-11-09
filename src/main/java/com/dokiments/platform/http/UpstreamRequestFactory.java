package com.dokiments.platform.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Creates each request on the JDK's HTTP client, with the attempt timeout that fits its method.
 *
 * <p>The JDK client is used on purpose, rather than whichever client Spring Boot would pick from the classpath: the
 * retry rules in {@link UpstreamRetryInterceptor} depend on the exact exceptions it throws to tell a request that never
 * left this process from one that may have reached the service.
 *
 * <p>The timeout covers the whole attempt, from sending the request until the response body has been read or closed.
 */
final class UpstreamRequestFactory implements ClientHttpRequestFactory {

    private final ClientHttpRequestFactory readOnlyRequests;
    private final ClientHttpRequestFactory otherRequests;

    UpstreamRequestFactory(HttpClient client, HttpClientProperties properties) {
        this.readOnlyRequests = withTimeout(client, properties.readAttemptTimeout());
        this.otherRequests = withTimeout(client, properties.attemptTimeout());
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod method) throws IOException {
        return (isReadOnly(method) ? readOnlyRequests : otherRequests).createRequest(uri, method);
    }

    /**
     * Whether the method only reads, so sending the request twice can't change anything twice. Other requests may spend
     * a single-use code or write data, so they are only sent again when they certainly never arrived.
     */
    static boolean isReadOnly(HttpMethod method) {
        return method.equals(HttpMethod.GET) || method.equals(HttpMethod.HEAD);
    }

    private static ClientHttpRequestFactory withTimeout(HttpClient client, Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(timeout);
        return factory;
    }
}

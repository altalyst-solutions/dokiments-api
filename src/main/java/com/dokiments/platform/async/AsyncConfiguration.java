package com.dokiments.platform.async;

import com.dokiments.platform.config.DokimentsProperties;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Lets a listener marked {@code @Async} run on its own thread. Spring Boot's task executor is used, which runs on
 * virtual threads, so waiting on Firestore or an email provider costs nothing.
 *
 * <p>With {@code dokiments.async.inline} ({@code ASYNC_INLINE=true}), {@code @Async} methods run on the caller's thread
 * instead, so they are done before the request answers. This is for platforms that give an instance CPU only while it
 * is answering a request, such as Cloud Run with request-based billing: there, work left running after the answer
 * stalls until the next request arrives, or is lost when the instance stops, and emails would go late or never. What a
 * listener throws is still caught and logged by Spring's async support, so it can't fail the request either way.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Bean
    AsyncConfigurer asyncConfigurer(DokimentsProperties properties) {
        boolean inline = properties.async().inline();
        if (inline) {
            log.info("@Async listeners run on the request's thread, before it answers (dokiments.async.inline)");
        }
        return new AsyncConfigurer() {
            // No executor of its own by default: Spring then uses Spring Boot's task executor.
            @Override
            public @Nullable Executor getAsyncExecutor() {
                return inline ? new SyncTaskExecutor() : null;
            }
        };
    }
}

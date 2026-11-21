package com.dokiments.platform.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Lets a listener marked {@code @Async} run on its own thread. Spring Boot's task executor is used, which runs on
 * virtual threads, so waiting on Firestore or an email provider costs nothing.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {}

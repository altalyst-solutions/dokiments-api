package com.dokiments.platform.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dokiments.platform.config.DokimentsProperties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * Checks where {@code @Async} listeners run: on a thread of their own by default, and on the publisher's thread, done
 * before it carries on, with {@code dokiments.async.inline}. In both, what a listener throws never reaches the
 * publisher, which is what keeps a failed email from failing the request that caused it.
 */
class AsyncConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfiguration.class, ProbeConfiguration.class);

    @Test
    void byDefaultAListenerRunsOnAThreadOfItsOwn() {
        contextRunner.run(context -> {
            context.publishEvent(new Probe());

            Thread ranOn = context.getBean(Calls.class).threads().poll(5, TimeUnit.SECONDS);
            assertThat(ranOn).isNotNull().isNotSameAs(Thread.currentThread());
        });
    }

    @Test
    void inlineAListenerHasRunOnThePublishersThreadWhenPublishingReturns() {
        contextRunner.withPropertyValues("dokiments.async.inline=true").run(context -> {
            context.publishEvent(new Probe());

            // No waiting: the listener has already run.
            assertThat(context.getBean(Calls.class).threads().poll()).isSameAs(Thread.currentThread());
        });
    }

    @Test
    void inlineAFailingListenerDoesNotReachThePublisher() {
        contextRunner.withPropertyValues("dokiments.async.inline=true").run(context -> {
            assertThatCode(() -> context.publishEvent(new Failure())).doesNotThrowAnyException();
            assertThat(context.getBean(Calls.class).threads().poll()).isSameAs(Thread.currentThread());
        });
    }

    @Test
    void byDefaultAFailingListenerDoesNotReachThePublisher() {
        contextRunner.run(context -> {
            assertThatCode(() -> context.publishEvent(new Failure())).doesNotThrowAnyException();
            assertThat(context.getBean(Calls.class).threads().poll(5, TimeUnit.SECONDS))
                    .isNotNull();
        });
    }

    record Probe() {}

    record Failure() {}

    /**
     * Where the listeners ran. A bean of its own, because the listeners' bean is a proxy (for {@code @Async}), whose
     * fields aren't the real object's.
     */
    record Calls(BlockingQueue<Thread> threads) {}

    static class Listeners {

        private final Calls calls;

        Listeners(Calls calls) {
            this.calls = calls;
        }

        @Async
        @EventListener
        void on(Probe probe) {
            calls.threads().add(Thread.currentThread());
        }

        @Async
        @EventListener
        void on(Failure failure) {
            calls.threads().add(Thread.currentThread());
            throw new IllegalStateException("a listener failing on purpose");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DokimentsProperties.class)
    static class ProbeConfiguration {

        @Bean
        Calls calls() {
            return new Calls(new LinkedBlockingQueue<>());
        }

        @Bean
        Listeners listeners(Calls calls) {
            return new Listeners(calls);
        }
    }
}

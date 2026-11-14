package com.dokiments.templates.adapter.out.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.templates.domain.Catalog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogCacheTest {

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger reads = new AtomicInteger();

    private Catalog read() {
        reads.incrementAndGet();
        return new Catalog(List.of(), List.of());
    }

    @Test
    void theCatalogIsReusedForTheConfiguredTimeThenReadAgain() {
        var cache = new CatalogCache(Duration.ofMinutes(5), clock);

        var first = cache.get(this::read);
        clock.now = clock.now.plus(Duration.ofMinutes(4));
        assertThat(cache.get(this::read)).isSameAs(first);
        assertThat(reads).hasValue(1);

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        cache.get(this::read);
        assertThat(reads).hasValue(2);
    }

    @Test
    void aFailedReadIsntKept() {
        var cache = new CatalogCache(Duration.ofMinutes(5), clock);

        assertThatThrownBy(() -> cache.get(() -> {
                    throw new IllegalStateException("Firestore is down");
                }))
                .isInstanceOf(IllegalStateException.class);
        cache.get(this::read);

        assertThat(reads).hasValue(1);
    }

    @Test
    void aTimeOfZeroReadsEveryTime() {
        var cache = new CatalogCache(Duration.ZERO, clock);

        cache.get(this::read);
        cache.get(this::read);

        assertThat(reads).hasValue(2);
    }

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-14T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

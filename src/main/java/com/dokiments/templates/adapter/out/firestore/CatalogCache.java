package com.dokiments.templates.adapter.out.firestore;

import com.dokiments.templates.domain.Catalog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the catalog for a while after reading it. Requests that arrive while it is being read wait for that one read
 * instead of starting their own, and a failed read isn't kept, so the next request reads again. A time of zero keeps
 * nothing.
 */
final class CatalogCache {

    private final Duration keepFor;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private @Nullable Kept kept;

    CatalogCache(Duration keepFor, Clock clock) {
        this.keepFor = keepFor;
        this.clock = clock;
    }

    Catalog get(Supplier<Catalog> read) {
        if (keepFor.isZero()) {
            return read.get();
        }
        lock.lock();
        try {
            Instant now = clock.instant();
            Kept current = kept;
            if (current != null && now.isBefore(current.until())) {
                return current.catalog();
            }
            Catalog catalog = read.get();
            kept = new Kept(catalog, now.plus(keepFor));
            return catalog;
        } finally {
            lock.unlock();
        }
    }

    private record Kept(Catalog catalog, Instant until) {}
}

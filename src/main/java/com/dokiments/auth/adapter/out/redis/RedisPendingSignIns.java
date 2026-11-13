package com.dokiments.auth.adapter.out.redis;

import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.domain.ReturnPath;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Pending Google sign-ins in Redis, under their {@code state}, for {@link PendingSignIns#LIFETIME}. Redis forgets them
 * by itself when they expire, and taking one removes it in the same step, so a {@code state} can't complete two
 * sign-ins.
 */
@Repository
class RedisPendingSignIns implements PendingSignIns {

    static final String KEY_PREFIX = "dokiments:google-sign-in:";

    private final StringRedisTemplate redis;

    RedisPendingSignIns(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String state, ReturnPath returnPath) {
        redis.opsForValue().set(KEY_PREFIX + state, returnPath.path(), LIFETIME);
    }

    @Override
    public Optional<ReturnPath> take(String state) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + state))
                .map(ReturnPath::of);
    }
}

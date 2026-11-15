package com.dokiments.auth.adapter.out.redis;

import com.dokiments.auth.application.port.out.PendingSignIns;
import com.dokiments.auth.application.port.out.PendingSignIns.PendingSignIn;
import com.dokiments.auth.domain.ReturnPath;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pending Google sign-ins in Redis, under their {@code state}, for {@link PendingSignIns#LIFETIME}. Redis forgets them
 * by itself when they expire, and taking one removes it in the same step, so a {@code state} can't complete two
 * sign-ins.
 */
@Repository
class RedisPendingSignIns implements PendingSignIns {

    static final String KEY_PREFIX = "dokiments:google-sign-in:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    RedisPendingSignIns(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void save(String state, PendingSignIn signIn) {
        var stored = new Stored(signIn.returnPath().path(), signIn.promoCode());
        redis.opsForValue().set(KEY_PREFIX + state, json.writeValueAsString(stored), LIFETIME);
    }

    @Override
    public Optional<PendingSignIn> take(String state) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + state))
                .map(this::read);
    }

    /**
     * A stored sign-in. One started before promo codes were kept with it holds only the path, as plain text, and still
     * completes for the few minutes it lives.
     */
    private PendingSignIn read(String value) {
        if (!value.startsWith("{")) {
            return new PendingSignIn(ReturnPath.of(value), null);
        }
        var stored = json.readValue(value, Stored.class);
        return new PendingSignIn(ReturnPath.of(stored.path()), stored.promoCode());
    }

    /** The stored form, as JSON text. */
    record Stored(String path, @Nullable String promoCode) {}
}

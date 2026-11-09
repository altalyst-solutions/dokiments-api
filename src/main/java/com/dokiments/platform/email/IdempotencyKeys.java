package com.dokiments.platform.email;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds idempotency keys for {@link EmailMessage}: Resend delivers each key at most once, so a retried send can't
 * deliver the same email twice.
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {}

    /**
     * {@code <prefix>-<64 hex characters>}: the parts joined with {@code |} and hashed. Hashed because Resend refuses
     * keys over 256 characters, and an email address alone can be 254.
     *
     * <p>Use stable facts (IDs, the time of the action), never the time of sending, so a retry gets the same key. For
     * example {@code hashed("signed", sharedDocumentId, signerId, signedAt)}.
     *
     * @param prefix names the kind of email, such as {@code share}
     */
    public static String hashed(String prefix, Object... parts) {
        var joined = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                joined.append('|');
            }
            joined.append(parts[i]);
        }
        return prefix + "-" + HexFormat.of().formatHex(sha256(joined.toString()));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}

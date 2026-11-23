package com.dokiments.sharing.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The secret in an invitation link, and the hash it is stored under. Only the hash is stored, as the invitation's
 * document ID, so whoever reads the stored invitations can't make a working link from them. The link alone is the key:
 * 32 random bytes, which can't be guessed.
 *
 * @param token the secret, as it goes in the link: 43 characters of URL-safe base 64
 * @param hash its SHA-256, in hexadecimal
 */
public record InviteToken(String token, String hash) {

    /** The shape of a token in a link, so anything else is refused before it is looked up. */
    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    /** A new token, from a strong random source. */
    public static InviteToken generate(RandomGenerator random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new InviteToken(token, hash(token));
    }

    /** The hash a token is stored under. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }

    /** Whether something in a link could be a token at all. */
    public static boolean hasShape(String value) {
        return SHAPE.matcher(value).matches();
    }
}

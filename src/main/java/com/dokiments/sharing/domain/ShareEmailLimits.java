package com.dokiments.sharing.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * How often sharing emails go out, as the web app limits them. Sharing again with the same person refreshes their copy
 * every time, but they are emailed about it at most once every ten minutes; and an owner's shares send at most twenty
 * emails a day in all, so an account can't be used to send mail in bulk.
 */
public final class ShareEmailLimits {

    /** How long after an email about a copy the same person isn't emailed about it again. */
    public static final Duration COOLDOWN = Duration.ofMinutes(10);

    /** How far back emails are counted, and kept on each recipient. */
    public static final Duration WINDOW = Duration.ofDays(1);

    /** How many sharing emails an owner's shares send within {@link #WINDOW}. */
    public static final int DAILY_CAP = 20;

    private ShareEmailLimits() {}

    /** Whether to send, or why not. */
    public enum Decision {
        SEND,
        COOLDOWN,
        DAILY_CAP
    }

    /**
     * Whether to email {@code recipientEmail} about {@code sharedDocumentId}, given every copy the owner has shared.
     */
    public static Decision decide(
            Instant now, String recipientEmail, String sharedDocumentId, Collection<SharedDocument> shares) {
        String email = recipientEmail.trim().toLowerCase(Locale.ROOT);
        Instant cooldownStart = now.minus(COOLDOWN);
        boolean recentlyEmailed = shares.stream()
                .filter(share -> share.id().equals(sharedDocumentId))
                .flatMap(share -> recentSends(share, email, now))
                .anyMatch(sentAt -> sentAt.isAfter(cooldownStart));
        if (recentlyEmailed) {
            return Decision.COOLDOWN;
        }
        long sentToday = shares.stream()
                .flatMap(share ->
                        share.recipients().stream().flatMap(recipient -> recentSends(share, recipient.email(), now)))
                .count();
        return sentToday >= DAILY_CAP ? Decision.DAILY_CAP : Decision.SEND;
    }

    /**
     * The recipient's email times, with this one added and any older than {@link #WINDOW} dropped, so the copy only
     * keeps what the limits count.
     */
    public static List<Instant> withSend(List<Instant> emailedAt, Instant sentAt) {
        Instant start = sentAt.minus(WINDOW);
        return Stream.concat(emailedAt.stream().filter(at -> at.isAfter(start)), Stream.of(sentAt))
                .toList();
    }

    private static Stream<Instant> recentSends(SharedDocument share, String email, Instant now) {
        Instant start = now.minus(WINDOW);
        return share.recipients().stream().filter(recipient -> recipient.email().equals(email)).findFirst().stream()
                .flatMap(recipient -> recipient.emailedAt().stream())
                .filter(at -> at.isAfter(start));
    }
}

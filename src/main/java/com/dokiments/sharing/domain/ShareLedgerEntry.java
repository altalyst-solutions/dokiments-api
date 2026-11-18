package com.dokiments.sharing.domain;

import java.time.Instant;

/**
 * Someone an owner has ever shared a document with. A plan's sharing limit counts these.
 *
 * @param email their address, lower-case
 * @param firstSharedAt when the owner first shared anything with them; never changes
 * @param lastSharedAt when the owner last shared something with them
 */
public record ShareLedgerEntry(String email, Instant firstSharedAt, Instant lastSharedAt) {}

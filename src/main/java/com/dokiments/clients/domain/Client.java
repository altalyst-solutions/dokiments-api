package com.dokiments.clients.domain;

import java.time.Instant;

/**
 * One of a user's clients.
 *
 * @param id the client's ID
 * @param details what the user keeps about the client
 * @param createdAt when the client was added
 * @param updatedAt when the details last changed; the same as {@code createdAt} until then
 */
public record Client(ClientId id, ClientDetails details, Instant createdAt, Instant updatedAt) {

    /** A client just added. */
    public static Client added(ClientId id, ClientDetails details, Instant now) {
        return new Client(id, details, now, now);
    }

    /** This client with {@code changes} made at {@code now}. */
    public Client changed(ClientChanges changes, Instant now) {
        return new Client(id, changes.applyTo(details), createdAt, now);
    }
}

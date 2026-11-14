package com.dokiments.clients.application.port.out;

import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Where each user's clients are kept. Every method works only on the list of the user {@code uid}. */
public interface ClientBook {

    /** All of the user's clients, in no particular order. */
    List<Client> all(String uid);

    Optional<Client> find(String uid, ClientId id);

    /** Stores a new client. */
    void add(String uid, Client client);

    /**
     * Stores only the given fields of {@code changes} and the time of the change, leaving the others as they are.
     *
     * @return {@code false} if the client no longer exists, in which case nothing is stored
     */
    boolean change(String uid, ClientId id, ClientChanges changes, Instant changedAt);

    /** Removes the client, if it exists. */
    void remove(String uid, ClientId id);
}

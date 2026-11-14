package com.dokiments.clients.application.port.in;

import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientDetails;
import java.util.List;

/**
 * A user's own list of clients: the contact details they reuse when preparing documents.
 *
 * <p>Every method works only on the list of the user {@code uid}, the signed-in user. There is no plan limit on how
 * many clients a user keeps.
 */
public interface ManageClients {

    /** The user's clients, the most recently added first. Empty for a user who has none. */
    List<Client> list(String uid);

    /** Adds a client, with a new ID. */
    Client add(String uid, ClientDetails details);

    /**
     * Changes some of a client's details and returns the client as changed.
     *
     * @throws com.dokiments.clients.domain.ClientNotFoundException if the user has no client with this ID
     */
    Client change(String uid, String clientId, ClientChanges changes);

    /**
     * Removes a client. Removing one that doesn't exist is not an error. Documents already prepared for the client keep
     * its details, which were copied into them.
     */
    void remove(String uid, String clientId);
}

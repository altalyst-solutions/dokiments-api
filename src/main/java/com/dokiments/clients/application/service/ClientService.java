package com.dokiments.clients.application.service;

import com.dokiments.clients.application.port.in.ManageClients;
import com.dokiments.clients.application.port.out.ClientBook;
import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientDetails;
import com.dokiments.clients.domain.ClientId;
import com.dokiments.clients.domain.ClientNotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

@Service
class ClientService implements ManageClients {

    /** Newest first. The sort is stable, so clients added at the same moment keep the order they are stored in. */
    private static final Comparator<Client> NEWEST_FIRST =
            Comparator.comparing(Client::createdAt).reversed();

    private final ClientBook book;
    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    ClientService(ClientBook book, Clock clock) {
        this.book = book;
        this.clock = clock;
    }

    @Override
    public List<Client> list(String uid) {
        // Sorted here rather than by Firestore, because a query ordered by a field leaves out the documents that
        // don't have it, and clients stored by older versions of the web app may not.
        return book.all(uid).stream().sorted(NEWEST_FIRST).toList();
    }

    @Override
    public Client add(String uid, ClientDetails details) {
        var now = clock.instant();
        var client = Client.added(ClientId.generate(now, random), details, now);
        book.add(uid, client);
        return client;
    }

    @Override
    public Client change(String uid, String clientId, ClientChanges changes) {
        var id = ClientId.parse(clientId).orElseThrow(ClientNotFoundException::new);
        // Read first, because the answer is the whole client, and so a change can't create a client that never
        // existed. Only the changed fields are written, so two changes to different fields at once both stay.
        var existing = book.find(uid, id).orElseThrow(ClientNotFoundException::new);
        var now = clock.instant();
        if (!book.change(uid, id, changes, now)) {
            // Removed after it was read.
            throw new ClientNotFoundException();
        }
        return existing.changed(changes, now);
    }

    @Override
    public void remove(String uid, String clientId) {
        ClientId.parse(clientId).ifPresent(id -> book.remove(uid, id));
    }
}

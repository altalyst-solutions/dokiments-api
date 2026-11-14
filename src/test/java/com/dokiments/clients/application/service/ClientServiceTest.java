package com.dokiments.clients.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.clients.application.port.out.ClientBook;
import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientDetails;
import com.dokiments.clients.domain.ClientId;
import com.dokiments.clients.domain.ClientNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ClientServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T09:30:00Z");
    private static final ClientDetails JANE = new ClientDetails("", "", "Acme", "jane@acme.example", "Jane", "", "555");

    private final FakeBook book = new FakeBook();
    private final ClientService service = new ClientService(book, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anAddedClientGetsANewIdAndTheTime() {
        var client = service.add("uid-1", JANE);

        assertThat(client.id().value()).startsWith("client_" + Long.toString(NOW.toEpochMilli(), 36) + "_");
        assertThat(client).isEqualTo(new Client(client.id(), JANE, NOW, NOW));
        assertThat(book.stored("uid-1")).containsExactly(client);
    }

    @Test
    void theListIsTheUsersOwnNewestFirst() {
        var older = stored("uid-1", "b", Instant.parse("2026-01-01T00:00:00Z"));
        var newer = stored("uid-1", "a", Instant.parse("2026-02-01T00:00:00Z"));
        var undated = stored("uid-1", "c", Instant.EPOCH);
        stored("uid-2", "d", NOW);

        assertThat(service.list("uid-1")).containsExactly(newer, older, undated);
        assertThat(service.list("uid-3")).isEmpty();
    }

    @Test
    void aChangeStoresOnlyTheChangesAndAnswersTheWholeClient() {
        var jane = stored("uid-1", "a", Instant.parse("2026-01-01T00:00:00Z"));
        var changes = ClientChanges.of(null, null, null, null, null, null, "777");

        var changed = service.change("uid-1", "a", changes);

        assertThat(changed.details().phone()).isEqualTo("777");
        assertThat(changed.details().name()).isEqualTo("Jane");
        assertThat(changed.createdAt()).isEqualTo(jane.createdAt());
        assertThat(changed.updatedAt()).isEqualTo(NOW);
        assertThat(book.changes).containsExactly(changes);
    }

    @Test
    void aClientThatIsntTheUsersOwnCantBeChanged() {
        stored("uid-2", "a", NOW);
        var changes = ClientChanges.of(null, null, null, null, "Eve", null, null);

        assertNotFound(() -> service.change("uid-1", "a", changes));
        assertNotFound(() -> service.change("uid-1", "a/b", changes));
        assertThat(book.changes).isEmpty();
    }

    @Test
    void aClientRemovedBeforeTheWriteIsNotFound() {
        stored("uid-1", "a", NOW);
        book.goneOnChange = true;

        assertNotFound(() -> service.change("uid-1", "a", ClientChanges.of(null, null, null, null, "Jo", null, null)));
    }

    @Test
    void removingIsQuietWhenThereIsNothingToRemove() {
        stored("uid-1", "a", NOW);

        service.remove("uid-1", "a");
        service.remove("uid-1", "a");
        service.remove("uid-1", "__name__");

        assertThat(book.stored("uid-1")).isEmpty();
    }

    private Client stored(String uid, String id, Instant createdAt) {
        var client = new Client(new ClientId(id), JANE, createdAt, createdAt);
        book.add(uid, client);
        return client;
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ClientNotFoundException.class).hasMessage("Client not found.");
    }

    /** Clients in memory, by user, in the order they were stored. */
    private static final class FakeBook implements ClientBook {

        private final Map<String, Map<ClientId, Client>> clients = new LinkedHashMap<>();
        private final List<ClientChanges> changes = new ArrayList<>();
        private boolean goneOnChange;

        List<Client> stored(String uid) {
            return List.copyOf(clients.getOrDefault(uid, Map.of()).values());
        }

        @Override
        public List<Client> all(String uid) {
            return stored(uid);
        }

        @Override
        public Optional<Client> find(String uid, ClientId id) {
            return Optional.ofNullable(clients.getOrDefault(uid, Map.of()).get(id));
        }

        @Override
        public void add(String uid, Client client) {
            clients.computeIfAbsent(uid, key -> new LinkedHashMap<>()).put(client.id(), client);
        }

        @Override
        public boolean change(String uid, ClientId id, ClientChanges changes, Instant changedAt) {
            if (goneOnChange) {
                return false;
            }
            this.changes.add(changes);
            return true;
        }

        @Override
        public void remove(String uid, ClientId id) {
            clients.getOrDefault(uid, new LinkedHashMap<>()).remove(id);
        }
    }
}

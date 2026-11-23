package com.dokiments.sharing.application.service;

import com.dokiments.sharing.application.port.out.InviteStore;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.SharedDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Invitations in memory, for the sharing use case tests, which can be told to fail. */
@NullMarked
final class InMemoryInvites implements InviteStore {

    final Map<String, Invite> stored = new LinkedHashMap<>();
    final List<String> refreshed = new ArrayList<>();
    boolean failing;

    @Override
    public void create(Invite invite) {
        failIfAsked();
        stored.put(invite.tokenHash(), invite);
    }

    @Override
    public Optional<Invite> find(String tokenHash) {
        return Optional.ofNullable(stored.get(tokenHash));
    }

    @Override
    public void refresh(SharedDocument copy) {
        failIfAsked();
        refreshed.add(copy.id());
    }

    @Override
    public void delete(String tokenHash) {
        failIfAsked();
        stored.remove(tokenHash);
    }

    @Override
    public void deleteAllFor(String ownerUid, String sharedDocumentId, @Nullable String keepTokenHash) {
        failIfAsked();
        stored.values()
                .removeIf(invite -> invite.ownerId().equals(ownerUid)
                        && invite.sharedDocumentId().equals(sharedDocumentId)
                        && !invite.tokenHash().equals(keepTokenHash));
    }

    private void failIfAsked() {
        if (failing) {
            throw new IllegalStateException("Firestore is down");
        }
    }
}

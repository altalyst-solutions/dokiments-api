package com.dokiments.sharing.application.port.out;

import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.SharedDocument;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Where invitations are kept, by the hash of their link's secret. */
public interface InviteStore {

    void create(Invite invite);

    Optional<Invite> find(String tokenHash);

    /** Brings the preview in every invitation to {@code copy} up to date with it. */
    void refresh(SharedDocument copy);

    void delete(String tokenHash);

    /** Deletes every invitation the owner sent to the copy, except the one with {@code keepTokenHash}, if given. */
    void deleteAllFor(String ownerUid, String sharedDocumentId, @Nullable String keepTokenHash);
}

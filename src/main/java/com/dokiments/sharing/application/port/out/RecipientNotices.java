package com.dokiments.sharing.application.port.out;

import com.dokiments.sharing.domain.DocumentShared;
import org.jspecify.annotations.Nullable;

/** How the person a copy is shared with is told about it. */
public interface RecipientNotices {

    /**
     * Tells them it was shared, or refreshed. Never throws.
     *
     * @param inviteToken the secret of an invitation for them to follow, or {@code null} to point them to their shared
     *     documents instead
     * @return whether the notice went out
     */
    boolean shared(DocumentShared event, @Nullable String inviteToken);
}

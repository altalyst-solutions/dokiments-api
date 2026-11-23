package com.dokiments.sharing.application.port.in;

import com.dokiments.sharing.domain.DocumentShared;

/** Telling the person a copy is shared with about it, in reaction to the event that announced it. */
public interface NotifyRecipients {

    /** Refreshes the copy's invitations and emails them, within the email limits. Never throws. */
    void documentShared(DocumentShared event);
}

package com.dokiments.sharing.application.port.in;

import com.dokiments.sharing.domain.DocumentSigned;

/** Telling the owner of a shared copy what happened to it, in reaction to the event that announced it. */
public interface NotifyOwners {

    /** Tells the owner that their copy was signed. Never throws. */
    void documentSigned(DocumentSigned event);
}

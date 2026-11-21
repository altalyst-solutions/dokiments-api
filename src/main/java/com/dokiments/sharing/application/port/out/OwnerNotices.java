package com.dokiments.sharing.application.port.out;

import com.dokiments.sharing.domain.DocumentSigned;

/** How the owner of a shared copy is told about what happened to it. */
public interface OwnerNotices {

    /**
     * Tells the owner that their copy was signed. Never throws: a notice that can't be delivered must never fail the
     * signature that caused it.
     */
    void signed(DocumentSigned event);
}

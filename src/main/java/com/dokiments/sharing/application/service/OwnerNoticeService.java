package com.dokiments.sharing.application.service;

import com.dokiments.sharing.application.port.in.NotifyOwners;
import com.dokiments.sharing.application.port.out.OwnerNotices;
import com.dokiments.sharing.domain.DocumentSigned;
import org.springframework.stereotype.Service;

@Service
class OwnerNoticeService implements NotifyOwners {

    private final OwnerNotices notices;

    OwnerNoticeService(OwnerNotices notices) {
        this.notices = notices;
    }

    @Override
    public void documentSigned(DocumentSigned event) {
        notices.signed(event);
    }
}

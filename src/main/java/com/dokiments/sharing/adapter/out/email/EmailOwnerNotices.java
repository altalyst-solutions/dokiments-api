package com.dokiments.sharing.adapter.out.email;

import com.dokiments.platform.email.EmailLabels;
import com.dokiments.platform.email.EmailMessage;
import com.dokiments.platform.email.EmailProperties;
import com.dokiments.platform.email.EmailSender;
import com.dokiments.platform.email.IdempotencyKeys;
import com.dokiments.sharing.application.port.out.OwnerNotices;
import com.dokiments.sharing.domain.DocumentSigned;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Tells the owner of a shared copy by email, with the web app's wording. The email never carries the signature itself,
 * only who signed and when.
 */
@Component
class EmailOwnerNotices implements OwnerNotices {

    private final EmailSender sender;
    private final URI siteUrl;

    EmailOwnerNotices(EmailSender sender, EmailProperties properties) {
        this.sender = sender;
        this.siteUrl = properties.siteUrl();
    }

    @Override
    public void signed(DocumentSigned event) {
        String signer = EmailLabels.person(event.signerName(), event.signerEmail());
        String document = EmailLabels.clean(event.documentName(), 80, "a document");
        var message = EmailMessage.of(
                        event.ownerEmail(),
                        signer + " signed \"" + document + "\"",
                        "sharing/document-signed",
                        Map.of(
                                "signerName",
                                signer,
                                "documentName",
                                document,
                                "signedAt",
                                EmailLabels.utcTime(event.signedAt()),
                                "url",
                                sharedCopyUrl(event.sharedDocumentId())))
                // The same signature makes the same key, so a retry delivers once.
                .withIdempotencyKey(IdempotencyKeys.hashed(
                        "signed",
                        event.sharedDocumentId(),
                        event.signerUid(),
                        event.signedAt().toEpochMilli()));
        // Sending never throws, and a failed email is already logged and counted by the sender.
        sender.send(message);
    }

    /** The shared copy in the web app, for the owner to open. */
    private String sharedCopyUrl(String sharedDocumentId) {
        String origin = siteUrl.toString().replaceAll("/+$", "");
        String id = URLEncoder.encode(sharedDocumentId, StandardCharsets.UTF_8).replace("+", "%20");
        return origin + "/my-documents/shared/" + id;
    }
}

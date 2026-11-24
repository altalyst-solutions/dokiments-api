package com.dokiments.sharing.adapter.out.email;

import com.dokiments.platform.email.EmailLabels;
import com.dokiments.platform.email.EmailMessage;
import com.dokiments.platform.email.EmailProperties;
import com.dokiments.platform.email.EmailResult;
import com.dokiments.platform.email.EmailSender;
import com.dokiments.platform.email.IdempotencyKeys;
import com.dokiments.sharing.application.port.out.RecipientNotices;
import com.dokiments.sharing.domain.DocumentShared;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Tells the person a copy is shared with by email, with the web app's wording and its invitation link. */
@Component
class EmailRecipientNotices implements RecipientNotices {

    private final EmailSender sender;
    private final URI siteUrl;

    EmailRecipientNotices(EmailSender sender, EmailProperties properties) {
        this.sender = sender;
        this.siteUrl = properties.siteUrl();
    }

    @Override
    public boolean shared(DocumentShared event, @Nullable String inviteToken) {
        String sender = EmailLabels.person(event.ownerName(), event.ownerEmail());
        String document = EmailLabels.clean(event.documentName(), 80, "a document");
        String subject = event.created()
                ? sender + " shared \"" + document + "\" with you"
                : sender + " updated \"" + document + "\"";
        var message = EmailMessage.of(
                        event.recipientEmail(),
                        subject,
                        "sharing/document-shared",
                        Map.of(
                                "updated",
                                !event.created(),
                                "signable",
                                event.signable(),
                                "senderName",
                                sender,
                                "documentName",
                                document,
                                "url",
                                url(inviteToken)))
                // One share makes one key, so a retry delivers once; sharing again makes a new one.
                .withIdempotencyKey(IdempotencyKeys.hashed(
                        "share",
                        event.sharedDocumentId(),
                        event.recipientEmail(),
                        event.sharedAt().toEpochMilli()));
        // Sending never throws, and a failed email is already logged and counted by the sender.
        return this.sender.send(message) instanceof EmailResult.Sent;
    }

    /** The invitation page for the link, or the shared documents page when there is no invitation. */
    private String url(@Nullable String inviteToken) {
        String origin = siteUrl.toString().replaceAll("/+$", "");
        return inviteToken == null ? origin + "/my-documents?tab=shared" : origin + "/shared/" + inviteToken;
    }
}

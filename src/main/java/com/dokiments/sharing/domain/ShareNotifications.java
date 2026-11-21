package com.dokiments.sharing.domain;

import com.dokiments.notifications.Notification;
import com.dokiments.shared.TypedText;
import java.util.ArrayList;
import java.util.List;

/**
 * The web app's two kinds of notification about sharing, worked out from the shared copies when asked for, as every
 * notification is: nothing is stored per user.
 */
public final class ShareNotifications {

    /** Someone shared a document with the user. */
    public static final String SHARED_KIND = "document-shared";

    /** Someone signed a document the user shared. */
    public static final String SIGNED_KIND = "document-signed";

    /** Where a notification about a document shared with the user leads. */
    static final String SHARED_TAB = "/my-documents?tab=shared";

    private ShareNotifications() {}

    /**
     * One notification for each copy shared with the user's address. It is stamped with when it was last shared with
     * them, which sharing again with the same person keeps, so refreshing a copy never notifies again. Copies taken
     * back from them, and ones that don't list them, are left out.
     *
     * @param email the user's address, lower-case
     */
    public static List<Notification> shared(List<SharedDocument> documents, String email) {
        var notifications = new ArrayList<Notification>();
        for (SharedDocument document : documents) {
            document.recipients().stream()
                    .filter(candidate -> candidate.email().equals(email))
                    .findFirst()
                    .filter(Recipient::hasAccess)
                    .ifPresent(recipient -> notifications.add(new Notification(
                            "document-shared:" + document.id(),
                            SHARED_KIND,
                            sender(document) + " shared a document",
                            title(document),
                            new Notification.Action("View shared documents", SHARED_TAB),
                            recipient.invitedAt())));
        }
        return notifications;
    }

    /**
     * One notification for each signature on a copy the user shared. It is stamped with when the person signed, which
     * never changes, so a signature notifies once. Copies owned by someone else are left out.
     */
    public static List<Notification> signed(List<SharedDocument> documents, String ownerUid) {
        var notifications = new ArrayList<Notification>();
        for (SharedDocument document : documents) {
            if (!document.ownerId().equals(ownerUid)) {
                continue;
            }
            document.signatures()
                    .forEach((signerUid, signature) -> notifications.add(new Notification(
                            "document-signed:" + document.id() + ":" + signerUid,
                            SIGNED_KIND,
                            signature.email() + " signed a document",
                            title(document),
                            new Notification.Action("View shared copy", "/my-documents/shared/" + document.id()),
                            signature.signedAt())));
        }
        return notifications;
    }

    /** The owner's name, or their address when they have none. */
    private static String sender(SharedDocument document) {
        String name = TypedText.trim(document.ownerName());
        return name.isEmpty() ? document.ownerEmail() : name;
    }

    /** The document's own title when it has one, otherwise its name. */
    private static String title(SharedDocument document) {
        String title = TypedText.trim(document.values().getOrDefault("title", ""));
        return title.isEmpty() ? document.name() : title;
    }
}

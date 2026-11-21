package com.dokiments.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.notifications.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The notifications about sharing, in the web app's wording. */
class ShareNotificationsTest {

    private static final Instant INVITED = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SIGNED = Instant.parse("2026-11-11T09:00:00Z");

    @Test
    void aSharedCopyNotifiesItsRecipientWhenItWasSharedWithThem() {
        var notifications = ShareNotifications.shared(List.of(copy("Alex Morgan", Map.of(), priya())), "p@x.co");

        assertThat(notifications)
                .containsExactly(new Notification(
                        "document-shared:owner_doc_1",
                        "document-shared",
                        "Alex Morgan shared a document",
                        "Website NDA",
                        new Notification.Action("View shared documents", "/my-documents?tab=shared"),
                        INVITED));
    }

    @Test
    void theOwnersAddressNamesThemWhenTheyHaveNoName() {
        var notifications = ShareNotifications.shared(List.of(copy(" ", Map.of(), priya())), "p@x.co");

        assertThat(notifications.getFirst().title()).isEqualTo("alex@example.com shared a document");
    }

    @Test
    void aTitleValueReplacesTheNameInTheBody() {
        var notifications =
                ShareNotifications.shared(List.of(copy("Alex", Map.of("title", " Q3 NDA "), priya())), "p@x.co");

        assertThat(notifications.getFirst().body()).isEqualTo("Q3 NDA");
    }

    @Test
    void copiesTakenBackOrNotListingTheUserAreLeftOut() {
        var takenBack = copy("Alex", Map.of(), priya()).revoked(SIGNED);

        assertThat(ShareNotifications.shared(List.of(takenBack), "p@x.co")).isEmpty();
        assertThat(ShareNotifications.shared(List.of(copy("Alex", Map.of(), priya())), "other@x.co"))
                .isEmpty();
    }

    @Test
    void aSignatureNotifiesTheOwnerWhenItWasSigned() {
        var signed = new SharedDocument(
                "owner_doc_1",
                INVITED,
                true,
                "Website NDA",
                "alex@example.com",
                "owner",
                "Alex",
                List.of(priya()),
                List.of(),
                Map.of("signer-1", new Signature("p@x.co", SIGNED, Signature.TYPED, "Priya Shah")),
                "doc_1",
                "nda",
                SIGNED,
                Map.of(),
                Map.of(),
                null);

        assertThat(ShareNotifications.signed(List.of(signed), "owner"))
                .containsExactly(new Notification(
                        "document-signed:owner_doc_1:signer-1",
                        "document-signed",
                        "p@x.co signed a document",
                        "Website NDA",
                        new Notification.Action("View shared copy", "/my-documents/shared/owner_doc_1"),
                        SIGNED));
        assertThat(ShareNotifications.signed(List.of(signed), "someone-else")).isEmpty();
    }

    private static Recipient priya() {
        return new Recipient("p@x.co", List.of(), INVITED, RecipientStatus.PENDING, null, null, null);
    }

    private static SharedDocument copy(String ownerName, Map<String, String> values, Recipient recipient) {
        return new SharedDocument(
                "owner_doc_1",
                INVITED,
                false,
                "Website NDA",
                "alex@example.com",
                "owner",
                ownerName,
                List.of(recipient),
                List.of(),
                Map.of(),
                "doc_1",
                "nda",
                INVITED,
                values,
                Map.of(),
                null);
    }
}

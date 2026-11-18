package com.dokiments.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.documents.OwnedDocument;
import com.dokiments.templates.TemplateSnapshot;
import com.dokiments.users.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SharingRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final Instant EARLIER = NOW.minus(Duration.ofDays(2));
    private static final TemplateSnapshot CONTRACT = template("recipientSignature");
    private static final OwnedDocument SOURCE = new OwnedDocument(
            "doc_1", "Contract", "classic-contract", Map.of("client", "Acme", "recipientSignature", "x"), CONTRACT);

    // Sharing

    @Test
    void aFirstShareMakesACopyWithoutTheRecipientsSignatureField() {
        var share = share(null, "client@acme.example");

        assertThat(share.created()).isTrue();
        var copy = share.document();
        assertThat(copy.id()).isEqualTo("uid-1_doc_1");
        assertThat(copy.values()).containsExactly(Map.entry("client", "Acme"));
        assertThat(copy.createdAt()).isEqualTo(NOW);
        assertThat(copy.recipients())
                .containsExactly(new Recipient(
                        "client@acme.example", List.of(), NOW, RecipientStatus.PENDING, null, null, null));
        assertThat(copy.recipientEmails()).containsExactly("client@acme.example");
        assertThat(copy.templateSnapshot()).isEqualTo(CONTRACT);
    }

    @Test
    void sharingAgainWithThePersonWhoHasItRefreshesTheCopyForThem() {
        var existing = share(null, "client@acme.example").document();
        var older = new SharedDocument(
                existing.id(),
                EARLIER,
                false,
                "Old name",
                "",
                "uid-1",
                "",
                existing.recipients(),
                Map.of(),
                "doc_1",
                "classic-contract",
                EARLIER,
                Map.of(),
                Map.of(),
                CONTRACT);

        var again = share(older, "client@acme.example");

        assertThat(again.created()).isFalse();
        assertThat(again.document().name()).isEqualTo("Contract");
        assertThat(again.document().createdAt()).isEqualTo(EARLIER);
        assertThat(again.recipient()).isEqualTo(existing.recipients().getFirst());
    }

    @Test
    void aCopyHasOneRecipientAtATimeAndASignedCopyNeverChanges() {
        var existing = share(null, "client@acme.example").document();

        assertThatThrownBy(() -> share(existing, "other@acme.example"))
                .isInstanceOf(SharedWithAnotherException.class)
                .hasMessage("This document is already shared with someone else. Sharing with more than one person is"
                        + " not available yet.");
        var signed = locked(existing);
        assertThatThrownBy(() -> share(signed, "client@acme.example"))
                .isInstanceOf(DocumentLockedException.class)
                .hasMessage("This document has been signed and can no longer be changed.");
    }

    @Test
    void sharingAgainAfterTakingItBackStartsAfresh() {
        var shared = share(null, "client@acme.example").document();
        var withView = new SharedDocument(
                shared.id(),
                shared.createdAt(),
                false,
                shared.name(),
                "",
                "uid-1",
                "",
                List.of(new Recipient(
                        "client@acme.example",
                        List.of(EARLIER),
                        EARLIER,
                        RecipientStatus.PENDING,
                        null,
                        null,
                        "uid-2")),
                Map.of(),
                "doc_1",
                "classic-contract",
                EARLIER,
                Map.of(),
                Map.of("uid-2", new View("client@acme.example", EARLIER), "uid-3", new View("x@y.z", EARLIER)),
                CONTRACT);
        var revoked = withView.revoked(NOW);

        var again = share(revoked, "client@acme.example");

        assertThat(again.created()).isTrue();
        assertThat(again.recipient().status()).isEqualTo(RecipientStatus.PENDING);
        assertThat(again.recipient().emailedAt()).containsExactly(EARLIER);
        assertThat(again.document().views()).containsOnlyKeys("uid-3");
    }

    // Statuses, as read

    @Test
    void aRecipientsStatusComesFromTheSignaturesAndViews() {
        var signedAt = NOW.minusSeconds(60);
        var copy = new SharedDocument(
                "id",
                NOW,
                true,
                "",
                "",
                "uid-1",
                "",
                List.of(
                        recipient("signed-by-uid@x.co", "uid-a"),
                        recipient("signed-by-email@x.co", null),
                        recipient("viewed@x.co", null),
                        recipient("pending@x.co", null)),
                Map.of(
                        "uid-a", new Signature("someone@x.co", signedAt, Signature.TYPED, "A"),
                        "uid-b", new Signature("signed-by-email@x.co", signedAt, Signature.TYPED, "B")),
                "",
                "",
                NOW,
                Map.of(),
                Map.of("uid-c", new View("viewed@x.co", NOW)),
                null);

        var statuses =
                copy.withStatuses().recipients().stream().map(Recipient::status).toList();

        assertThat(statuses)
                .containsExactly(
                        RecipientStatus.SIGNED,
                        RecipientStatus.SIGNED,
                        RecipientStatus.VIEWED,
                        RecipientStatus.PENDING);
        assertThat(copy.withStatuses().recipients().getFirst().signedAt()).isEqualTo(signedAt);
    }

    // Taking it back

    @Test
    void takingACopyBackLeavesItsRecipientWithoutAccess() {
        var shared = share(null, "client@acme.example").document();

        var revoked = shared.revoked(NOW.plusSeconds(1));

        assertThat(revoked.recipients().getFirst().status()).isEqualTo(RecipientStatus.REVOKED);
        assertThat(revoked.recipients().getFirst().revokedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(revoked.recipientEmails()).isEmpty();
        assertThat(revoked.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> revoked.revoked(NOW))
                .isInstanceOf(NotSharedException.class)
                .hasMessage("This document is not shared with anyone.");
        assertThatThrownBy(() -> locked(shared).revoked(NOW)).isInstanceOf(DocumentLockedException.class);
    }

    // The policy

    @Test
    void nobodySharesWithThemselvesAndAPlanLimitCountsNewPeopleOnly() {
        assertThatThrownBy(() -> SharePolicy.check("jane@example.com", " Jane@Example.com", List.of(), null))
                .isInstanceOf(SelfShareException.class)
                .hasMessage("You cannot share a document with yourself.");
        var ledger = List.of("a@x.co", "b@x.co");
        assertThatThrownBy(() -> SharePolicy.check("c@x.co", "jane@example.com", ledger, 2))
                .isInstanceOf(ShareLimitReachedException.class)
                .hasMessage("You have reached the sharing limit for your plan.");
        SharePolicy.check("a@x.co", "jane@example.com", ledger, 2);
        SharePolicy.check("c@x.co", "jane@example.com", ledger, null);
        for (UserRole role : UserRole.values()) {
            assertThat(SharePolicy.limitFor(role)).as(role.name()).isNull();
        }
    }

    @Test
    void onlyADocumentWithARecipientSignatureIsSignable() {
        assertThat(SharePolicy.isSignable(CONTRACT)).isTrue();
        assertThat(SharePolicy.isSignable(template("signature"))).isFalse();
        assertThat(SharePolicy.isSignable(null)).isFalse();
    }

    @Test
    void anAddressToShareWithIsTrimmedCheckedAndLowerCased() {
        assertThat(ShareEmails.typed(" Client@Acme.Example ")).isEqualTo("client@acme.example");
        assertThatThrownBy(() -> ShareEmails.typed("not an address"))
                .isInstanceOf(InvalidShareException.class)
                .hasMessage("Invalid request body.");
    }

    // Helpers

    private static Share share(@Nullable SharedDocument existing, String email) {
        return Share.of(existing, SOURCE, "uid-1", "jane@example.com", "Jane", email, NOW);
    }

    private static SharedDocument locked(SharedDocument document) {
        return new SharedDocument(
                document.id(),
                document.createdAt(),
                true,
                document.name(),
                document.ownerEmail(),
                document.ownerId(),
                document.ownerName(),
                document.recipients(),
                document.signatures(),
                document.sourceDocumentId(),
                document.templateId(),
                document.updatedAt(),
                document.values(),
                document.views(),
                document.templateSnapshot());
    }

    private static Recipient recipient(String email, @Nullable String uid) {
        return new Recipient(email, List.of(), NOW, RecipientStatus.PENDING, null, null, uid);
    }

    private static TemplateSnapshot template(String fieldKey) {
        return new TemplateSnapshot(
                "Contract",
                "",
                "contract",
                List.of(new TemplateSnapshot.Field(fieldKey, "Signature", null, "text")),
                new TemplateSnapshot.Style("classic", "Classic", "", "free"));
    }
}

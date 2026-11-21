package com.dokiments.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.documents.OwnedDocument;
import com.dokiments.templates.TemplateSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Who may open and sign a shared copy. The web app's Firestore rules decide this for it, so these are the rules the
 * Admin SDK, which ignores them, can't enforce.
 */
class SharingAccessRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final Viewer OWNER = new Viewer("owner", "owner@example.com", true);
    private static final Viewer CLIENT = new Viewer("client", "client@acme.example", true);
    private static final Viewer UNVERIFIED = new Viewer("client", "client@acme.example", false);
    private static final Viewer STRANGER = new Viewer("stranger", "stranger@example.com", true);

    // Opening

    @Test
    void theOwnerAndAVerifiedRecipientCanOpenIt() {
        var copy = copy(false, CLIENT.email(), List.of(), Map.of(), signable());

        assertThat(copy.canBeOpenedBy(OWNER)).isTrue();
        assertThat(copy.canBeOpenedBy(CLIENT)).isTrue();
        assertThat(copy.canBeOpenedBy(STRANGER)).isFalse();
    }

    @Test
    void anAddressCountsOnlyWhenItIsVerified() {
        var copy = copy(false, CLIENT.email(), List.of(), Map.of(), signable());

        assertThat(copy.canBeOpenedBy(UNVERIFIED)).isFalse();
    }

    @Test
    void anAccountAnInviteLinkedOpensItWithoutAVerifiedAddress() {
        var copy = copy(false, CLIENT.email(), List.of(CLIENT.uid()), Map.of(), signable());

        assertThat(copy.canBeOpenedBy(UNVERIFIED)).isTrue();
    }

    @Test
    void aRecipientTakenBackFromCanNoLongerOpenIt() {
        var revoked = copy(false, CLIENT.email(), List.of(CLIENT.uid()), Map.of(), signable())
                .revoked(NOW);

        assertThat(revoked.canBeOpenedBy(CLIENT)).isFalse();
        assertThat(revoked.recipientUids()).isEmpty();
    }

    // Signing

    @Test
    void aRecipientSignsAndTheCopyIsLockedForGood() {
        var signed =
                copy(false, CLIENT.email(), List.of(), Map.of(), signable()).signedBy(CLIENT, "Client Name", NOW);

        assertThat(signed.locked()).isTrue();
        assertThat(signed.updatedAt()).isEqualTo(NOW);
        assertThat(signed.signatures())
                .containsExactly(
                        Map.entry("client", new Signature("client@acme.example", NOW, Signature.TYPED, "Client Name")));
        assertThat(signed.recipients().getFirst().status()).isEqualTo(RecipientStatus.SIGNED);
        assertThatThrownBy(() -> signed.signedBy(CLIENT, "Again", NOW)).isInstanceOf(DocumentLockedException.class);
    }

    @Test
    void onlyARecipientWithAccessCanSign() {
        var copy = copy(false, CLIENT.email(), List.of(), Map.of(), signable());

        for (Viewer viewer : List.of(OWNER, STRANGER, UNVERIFIED)) {
            assertThatThrownBy(() -> copy.signedBy(viewer, "x", NOW))
                    .as(viewer.toString())
                    .isInstanceOf(NotRecipientException.class)
                    .hasMessage("You do not have access to this shared document.");
        }
        assertThatThrownBy(() -> copy.revoked(NOW).signedBy(CLIENT, "x", NOW))
                .isInstanceOf(NotRecipientException.class);
    }

    @Test
    void aCopySharedToViewCannotBeSigned() {
        var copy = copy(false, CLIENT.email(), List.of(), Map.of(), template("senderSignature"));

        assertThatThrownBy(() -> copy.signedBy(CLIENT, "x", NOW))
                .isInstanceOf(ViewOnlyException.class)
                .hasMessage("This document has no recipient signature. It was shared to view, so it cannot be signed.");
        assertThatThrownBy(() ->
                        copy(false, CLIENT.email(), List.of(), Map.of(), null).signedBy(CLIENT, "x", NOW))
                .isInstanceOf(ViewOnlyException.class);
    }

    @Test
    void aSignatureIsTrimmedAndAtMostTwoHundredCharacters() {
        assertThat(Signature.typed("  Jane Doe \u00A0")).isEqualTo("Jane Doe");
        assertThat(Signature.typed("x".repeat(200))).hasSize(200);
        for (String invalid : new String[] {null, "", "   ", "x".repeat(201)}) {
            assertThatThrownBy(() -> Signature.typed(invalid)).isInstanceOf(InvalidShareException.class);
        }
    }

    // First opening

    @Test
    void aRecipientsFirstOpeningIsNotedOnceAndNeverForAnythingElse() {
        var copy = copy(false, CLIENT.email(), List.of(), Map.of(), signable());

        assertThat(copy.firstViewBy(CLIENT, NOW)).contains(new View("client@acme.example", NOW));
        assertThat(copy.firstViewBy(OWNER, NOW)).isEmpty();
        assertThat(copy.firstViewBy(STRANGER, NOW)).isEmpty();
        assertThat(copy.firstViewBy(UNVERIFIED, NOW)).isEmpty();
        assertThat(copy(false, CLIENT.email(), List.of(), Map.of("client", new View(CLIENT.email(), NOW)), signable())
                        .firstViewBy(CLIENT, NOW.plusSeconds(1)))
                .isEmpty();
        assertThat(copy(true, CLIENT.email(), List.of(), Map.of(), signable()).firstViewBy(CLIENT, NOW))
                .isEmpty();
        assertThat(copy.revoked(NOW).firstViewBy(CLIENT, NOW)).isEmpty();
    }

    // Accounts an invitation link added

    @Test
    void sharingAgainKeepsTheAccountsAnInviteLinkedAndSharingAfterTakingItBackClearsThem() {
        var source = new OwnedDocument("doc_1", "Contract", "classic-contract", Map.of(), signable());
        var linked = copy(false, CLIENT.email(), List.of(CLIENT.uid()), Map.of(), signable());

        var refreshed = Share.of(linked, source, "owner", "owner@example.com", "Owner", CLIENT.email(), NOW);
        assertThat(refreshed.document().recipientUids()).containsExactly("client");

        var again = Share.of(linked.revoked(NOW), source, "owner", "owner@example.com", "Owner", CLIENT.email(), NOW);
        assertThat(again.document().recipientUids()).isEmpty();
    }

    // Helpers

    private static SharedDocument copy(
            boolean locked,
            String recipient,
            List<String> uids,
            Map<String, View> views,
            @Nullable TemplateSnapshot template) {
        return new SharedDocument(
                "owner_doc_1",
                NOW,
                locked,
                "Contract",
                OWNER.email(),
                OWNER.uid(),
                "Owner",
                List.of(new Recipient(recipient, List.of(), NOW, RecipientStatus.PENDING, null, null, null)),
                uids,
                Map.of(),
                "doc_1",
                "classic-contract",
                NOW,
                Map.of(),
                views,
                template);
    }

    private static TemplateSnapshot signable() {
        return template(SharePolicy.RECIPIENT_SIGNATURE_KEY);
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

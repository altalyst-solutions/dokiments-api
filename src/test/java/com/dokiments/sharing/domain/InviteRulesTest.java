package com.dokiments.sharing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.templates.TemplateSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class InviteRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-23T09:00:00Z");
    private static final Viewer CLIENT = new Viewer("client", "client@acme.example", false);

    // Tokens

    @Test
    void aTokenIsFortyThreeUrlSafeCharactersStoredOnlyAsItsHash() {
        var token = InviteToken.generate(new Random(1));

        assertThat(token.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(InviteToken.hasShape(token.token())).isTrue();
        assertThat(token.hash()).isEqualTo(InviteToken.hash(token.token())).matches("[0-9a-f]{64}");
        assertThat(InviteToken.generate(new Random(2)).token()).isNotEqualTo(token.token());
    }

    @Test
    void onlySomethingShapedLikeATokenIsLookedUp() {
        assertThat(InviteToken.hasShape("x".repeat(42))).isFalse();
        assertThat(InviteToken.hasShape("x".repeat(44))).isFalse();
        assertThat(InviteToken.hasShape("x".repeat(42) + "=")).isFalse();
        assertThat(InviteToken.hasShape("../" + "x".repeat(40))).isFalse();
        // The SHA-256 of "abc", so the hash matches the web app's for the same token.
        assertThat(InviteToken.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // Invitations

    @Test
    void anInvitationKeepsAPreviewOfTheCopyAndWorksForFourteenDays() {
        var invite = Invite.of(copy(false, signable()), " Client@Acme.example ", "hash", NOW);

        assertThat(invite.email()).isEqualTo("client@acme.example");
        assertThat(invite.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(invite.name()).isEqualTo("Contract");
        assertThat(invite.ownerId()).isEqualTo("owner");
        assertThat(invite.values()).containsExactly(Map.entry("client", "Acme"));
        assertThat(invite.isLive(NOW.plus(Duration.ofDays(14)).minusMillis(1))).isTrue();
        assertThat(invite.isLive(NOW.plus(Duration.ofDays(14)))).isFalse();
    }

    @Test
    void aPreviewOffersToSignUnlessTheTemplateCopySaysViewOnly() {
        assertThat(Invite.of(copy(false, signable()), "a@b.co", "h", NOW).signable())
                .isTrue();
        assertThat(Invite.of(copy(false, template("senderSignature")), "a@b.co", "h", NOW)
                        .signable())
                .isFalse();
        // Without a template copy, the preview counts it as signable, as the web app does.
        assertThat(Invite.of(copy(false, null), "a@b.co", "h", NOW).signable()).isTrue();
    }

    @Test
    void anAddressIsMaskedAsTheWebAppMasksIt() {
        assertThat(ShareEmails.mask("jane@example.com")).isEqualTo("j***@example.com");
        assertThat(ShareEmails.mask("@example.com")).isEqualTo("***@example.com");
        assertThat(ShareEmails.mask("nobody")).isEqualTo("n***@");
    }

    // Accepting

    @Test
    void anInvitationLinksTheInvitedAddressToItsCopyWhileItIsShared() {
        var copy = copy(false, signable());
        var invite = Invite.of(copy, CLIENT.email(), "hash", NOW);

        assertThat(copy.acceptsInvite(invite, CLIENT, NOW.plusSeconds(60))).isTrue();
        assertThat(copy.withRecipientUid(CLIENT).recipientUids()).containsExactly("client");
        assertThat(copy.withRecipientUid(CLIENT).withRecipientUid(CLIENT).recipientUids())
                .containsExactly("client");
    }

    @Test
    void anInvitationNoLongerAppliesWhenAnythingChanged() {
        var copy = copy(false, signable());
        var invite = Invite.of(copy, CLIENT.email(), "hash", NOW);

        assertThat(copy(true, signable()).acceptsInvite(invite, CLIENT, NOW))
                .as("signed")
                .isFalse();
        assertThat(copy.revoked(NOW).acceptsInvite(invite, CLIENT, NOW))
                .as("taken back")
                .isFalse();
        assertThat(copy.acceptsInvite(invite, CLIENT, NOW.plus(Invite.LIFETIME)))
                .as("expired")
                .isFalse();
        var someoneElse = new Viewer("other", "other@acme.example", true);
        assertThat(copy.acceptsInvite(invite, someoneElse, NOW))
                .as("another address")
                .isFalse();
        var forAnotherCopy = Invite.of(copy(false, signable()), CLIENT.email(), "hash", NOW);
        var elsewhere = new Invite(
                "hash",
                NOW,
                CLIENT.email(),
                NOW.plus(Invite.LIFETIME),
                "",
                "",
                "owner",
                "",
                "owner_doc_2",
                "",
                null,
                Map.of());
        assertThat(copy.acceptsInvite(elsewhere, CLIENT, NOW))
                .as("another copy")
                .isFalse();
        var fromAnother = new Invite(
                "hash",
                NOW,
                CLIENT.email(),
                NOW.plus(Invite.LIFETIME),
                "",
                "",
                "intruder",
                "",
                forAnotherCopy.sharedDocumentId(),
                "",
                null,
                Map.of());
        assertThat(copy.acceptsInvite(fromAnother, CLIENT, NOW))
                .as("another owner")
                .isFalse();
    }

    // Email limits

    @Test
    void aPersonIsEmailedAboutACopyAtMostOnceEveryTenMinutes() {
        var shares = List.of(copy(sentAt(NOW.minus(Duration.ofMinutes(9)))));

        assertThat(ShareEmailLimits.decide(NOW, " Client@acme.example", "owner_doc_1", shares))
                .isEqualTo(ShareEmailLimits.Decision.COOLDOWN);
        assertThat(ShareEmailLimits.decide(
                        NOW.plus(Duration.ofMinutes(1)), "client@acme.example", "owner_doc_1", shares))
                .isEqualTo(ShareEmailLimits.Decision.SEND);
    }

    @Test
    void anOwnersSharesSendAtMostTwentyEmailsADay() {
        List<Instant> nineteen = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            nineteen.add(NOW.minus(Duration.ofHours(1)).minusSeconds(i));
        }
        var twenty = new ArrayList<>(nineteen);
        twenty.add(NOW.minus(Duration.ofHours(2)));
        var old = new ArrayList<>(nineteen);
        old.add(NOW.minus(Duration.ofDays(1)));

        assertThat(ShareEmailLimits.decide(NOW, "client@acme.example", "another", List.of(copy(nineteen))))
                .isEqualTo(ShareEmailLimits.Decision.SEND);
        assertThat(ShareEmailLimits.decide(NOW, "client@acme.example", "another", List.of(copy(twenty))))
                .isEqualTo(ShareEmailLimits.Decision.DAILY_CAP);
        assertThat(ShareEmailLimits.decide(NOW, "client@acme.example", "another", List.of(copy(old))))
                .isEqualTo(ShareEmailLimits.Decision.SEND);
    }

    @Test
    void anEmailIsNotedOnTheCopyKeepingOnlyTheLastDay() {
        var earlier = NOW.minus(Duration.ofHours(3));
        var copy = copy(List.of(NOW.minus(Duration.ofDays(2)), earlier));

        var noted = copy.withEmailSent("client@acme.example", NOW).orElseThrow();

        assertThat(noted.recipients().getFirst().emailedAt()).containsExactly(earlier, NOW);
        assertThat(noted.updatedAt()).isEqualTo(copy.updatedAt());
        assertThat(copy.withEmailSent("stranger@example.com", NOW)).isEmpty();
        assertThat(copy(true, signable()).withEmailSent("client@acme.example", NOW))
                .isEmpty();
    }

    // Helpers

    private static List<Instant> sentAt(Instant at) {
        return List.of(at);
    }

    private static SharedDocument copy(boolean locked, @Nullable TemplateSnapshot template) {
        return copy(locked, template, List.of());
    }

    private static SharedDocument copy(List<Instant> emailedAt) {
        return copy(false, signable(), emailedAt);
    }

    private static SharedDocument copy(boolean locked, @Nullable TemplateSnapshot template, List<Instant> emailedAt) {
        return new SharedDocument(
                "owner_doc_1",
                NOW,
                locked,
                "Contract",
                "owner@example.com",
                "owner",
                "Owner",
                List.of(new Recipient(
                        "client@acme.example", emailedAt, NOW, RecipientStatus.PENDING, null, null, null)),
                List.of(),
                Map.of(),
                "doc_1",
                "classic-contract",
                NOW.minus(Duration.ofDays(1)),
                Map.of("client", "Acme"),
                Map.of(),
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

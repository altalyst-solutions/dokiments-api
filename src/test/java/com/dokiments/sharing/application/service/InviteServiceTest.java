package com.dokiments.sharing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dokiments.platform.config.DokimentsProperties;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.InviteExpiredException;
import com.dokiments.sharing.domain.InviteInvalidException;
import com.dokiments.sharing.domain.InviteMismatchException;
import com.dokiments.sharing.domain.InviteStatus;
import com.dokiments.sharing.domain.InviteToken;
import com.dokiments.sharing.domain.Recipient;
import com.dokiments.sharing.domain.RecipientStatus;
import com.dokiments.sharing.domain.SharedDocument;
import com.dokiments.support.TestUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class InviteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T09:00:00Z");
    private static final TestUser CLIENT = new TestUser("client", "Client@Acme.example", "Client", false);

    private final InMemoryInvites invites = new InMemoryInvites();
    private final InMemoryCopies copies = new InMemoryCopies();
    private final SharedDocument copy = copy();
    private final InviteToken token = InviteToken.generate(new Random(7));

    InviteServiceTest() {
        copies.put(copy);
        invites.create(Invite.of(copy, "client@acme.example", token.hash(), NOW.minus(Duration.ofDays(1))));
    }

    @Test
    void aWorkingLinkShowsItsInvitationAndAnyOtherLinkShowsWhy() {
        var service = service(true, NOW);

        var valid = service.preview(token.token());
        assertThat(valid.status()).isEqualTo(InviteStatus.VALID);
        assertThat(valid.invite()).isNotNull();
        assertThat(service.preview(InviteToken.generate(new Random(8)).token()).status())
                .isEqualTo(InviteStatus.INVALID);
        assertThat(service.preview("not-a-token").status()).isEqualTo(InviteStatus.INVALID);
        assertThat(service(true, NOW.plus(Duration.ofDays(14)))
                        .preview(token.token())
                        .status())
                .isEqualTo(InviteStatus.EXPIRED);
    }

    @Test
    void acceptingLinksTheInvitedAccountEvenWithoutAVerifiedAddress() {
        assertThat(service(true, NOW).accept(CLIENT, token.token())).isEqualTo("owner_doc_1");

        assertThat(copies.stored.get("owner_doc_1").recipientUids()).containsExactly("client");
    }

    @Test
    void anAccountThatCanOpenTheCopyAlreadyIsSentStraightToIt() {
        var verified = new TestUser("client-2", "client@acme.example", "Client", true);

        assertThat(service(true, NOW).accept(verified, token.token())).isEqualTo("owner_doc_1");

        assertThat(copies.stored.get("owner_doc_1").recipientUids()).isEmpty();
    }

    @Test
    void anotherAddressIsToldWhichAddressWasInvitedMasked() {
        var eve = new TestUser("eve", "eve@example.com", "Eve", true);

        assertThatThrownBy(() -> service(true, NOW).accept(eve, token.token()))
                .isInstanceOf(InviteMismatchException.class)
                .hasMessage("This invitation was sent to a different email address.")
                .satisfies(e -> assertThat(((InviteMismatchException) e).publicFields())
                        .containsExactly(Map.entry("invitedEmail", "c***@acme.example")));
    }

    @Test
    void anExpiredUnknownOrNoLongerApplyingLinkIsRefused() {
        assertThatThrownBy(() -> service(true, NOW.plus(Duration.ofDays(14))).accept(CLIENT, token.token()))
                .isInstanceOf(InviteExpiredException.class)
                .hasMessage("This invitation has expired.");
        assertThatThrownBy(() -> service(true, NOW).accept(CLIENT, "x".repeat(43)))
                .isInstanceOf(InviteInvalidException.class)
                .hasMessage("This invitation is no longer valid.");

        copies.put(copy.revoked(NOW));
        assertThatThrownBy(() -> service(true, NOW).accept(CLIENT, token.token()))
                .isInstanceOf(InviteInvalidException.class);
    }

    @Test
    void whileSharingIsOffInvitationsDontExist() {
        var service = service(false, NOW);

        assertThatThrownBy(() -> service.preview(token.token())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.accept(CLIENT, token.token())).isInstanceOf(NotFoundException.class);
    }

    private InviteService service(boolean enabled, Instant now) {
        return new InviteService(
                invites,
                copies,
                new DokimentsProperties(new DokimentsProperties.Features(enabled, true)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static SharedDocument copy() {
        return new SharedDocument(
                "owner_doc_1",
                NOW,
                false,
                "Contract",
                "owner@example.com",
                "owner",
                "Owner",
                List.of(new Recipient(
                        "client@acme.example", List.of(), NOW, RecipientStatus.PENDING, null, null, null)),
                List.of(),
                Map.of(),
                "doc_1",
                "classic-contract",
                NOW,
                Map.of(),
                Map.of(),
                null);
    }
}

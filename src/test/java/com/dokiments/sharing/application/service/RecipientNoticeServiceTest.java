package com.dokiments.sharing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.sharing.application.port.out.RecipientNotices;
import com.dokiments.sharing.domain.DocumentShared;
import com.dokiments.sharing.domain.Invite;
import com.dokiments.sharing.domain.InviteToken;
import com.dokiments.sharing.domain.Recipient;
import com.dokiments.sharing.domain.RecipientStatus;
import com.dokiments.sharing.domain.SharedDocument;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@NullMarked
class RecipientNoticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T09:00:00.123456Z");
    private static final DocumentShared SHARED = new DocumentShared(
            "owner_doc_1", "owner", "owner@example.com", "Owner", "client@acme.example", "Contract", true, true, NOW);

    private final InMemoryCopies copies = new InMemoryCopies();
    private final InMemoryInvites invites = new InMemoryInvites();
    private final List<@Nullable String> sentWithTokens = new ArrayList<>();
    private boolean emailFails;
    private final RecipientNotices notices = (event, token) -> {
        sentWithTokens.add(token);
        return !emailFails;
    };
    private final RecipientNoticeService service =
            new RecipientNoticeService(copies, invites, notices, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theRecipientIsEmailedANewInvitationAndOlderOnesGo() {
        copies.put(copy(List.of()));
        invites.create(Invite.of(copies.stored.get("owner_doc_1"), "client@acme.example", "old", NOW));

        service.documentShared(SHARED);

        assertThat(sentWithTokens).hasSize(1);
        String token = sentWithTokens.getFirst();
        assertThat(token).isNotNull();
        assertThat(invites.stored).containsOnlyKeys(InviteToken.hash(token));
        assertThat(invites.stored.values().iterator().next().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(copies.stored.get("owner_doc_1").recipients().getFirst().emailedAt())
                .containsExactly(Instant.parse("2026-11-23T09:00:00.123Z"));
        assertThat(invites.refreshed).containsExactly("owner_doc_1");
    }

    @Test
    void aFailedEmailLeavesNoInvitationAndNoNote() {
        copies.put(copy(List.of()));
        emailFails = true;

        service.documentShared(SHARED);

        assertThat(sentWithTokens).hasSize(1);
        assertThat(invites.stored).isEmpty();
        assertThat(copies.emailsNoted).isEmpty();
    }

    @Test
    void anEmailWithinTenMinutesOfTheLastIsHeldBack() {
        copies.put(copy(List.of(NOW.minus(Duration.ofMinutes(5)))));

        service.documentShared(SHARED);

        assertThat(sentWithTokens).isEmpty();
        assertThat(invites.stored).isEmpty();
    }

    @Test
    void withoutAnInvitationTheEmailStillGoesWithoutALink() {
        copies.put(copy(List.of()));
        invites.failing = true;

        service.documentShared(SHARED);

        assertThat(sentWithTokens).containsExactly((String) null);
        assertThat(copies.emailsNoted).containsExactly("client@acme.example");
    }

    private static SharedDocument copy(List<Instant> emailedAt) {
        return new SharedDocument(
                "owner_doc_1",
                NOW,
                false,
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
                NOW,
                Map.of(),
                Map.of(),
                null);
    }
}

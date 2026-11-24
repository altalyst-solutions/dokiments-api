package com.dokiments.sharing.adapter.in.web;

import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.application.port.in.ManageInvites;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitation links, which the share email carries, so someone without an account can preview a shared copy and link the
 * account they create to it. Paths, bodies, status codes and messages are the web app's. Both answer {@code 404 Not
 * found.} while sharing is switched off ({@code DOCUMENT_SHARING_ENABLED}).
 */
@RestController
@Tag(name = "Invitations")
class InvitesController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageInvites invites;

    InvitesController(ManageInvites invites) {
        this.invites = invites;
    }

    @GetMapping("/api/shared-invites/{token}")
    @Operation(
            operationId = "previewInvite",
            summary = "Preview an invitation",
            description = "Public: whoever has the link may see what it leads to, signed in or not. state is valid,"
                    + " with the invitation, or expired (after 14 days) or invalid, without it. Never cached.")
    @ApiResponse(responseCode = "200", description = "What the link leads to")
    @ApiResponse(
            responseCode = "404",
            description = "Sharing is off (\"Not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<InvitePreviewResponse> preview(@PathVariable String token) {
        var preview = invites.preview(token);
        var invite = preview.invite();
        var summary = invite == null
                ? null
                : new InviteSummary(invite.name(), invite.ownerEmail(), invite.ownerName(), invite.signable());
        // The answer depends on the moment (it expires) and on the link, which is a secret, so nothing keeps it.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new InvitePreviewResponse(summary, preview.status().value()));
    }

    @PostMapping("/api/shared-invites/{token}/accept")
    @Operation(
            operationId = "acceptInvite",
            summary = "Accept an invitation",
            description = "Links the signed-in account to the shared copy, when its address is the invited one, and"
                    + " answers the copy's ID to open. An account that can open the copy already is sent straight to"
                    + " it.")
    @ApiResponse(responseCode = "200", description = "Linked")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "403",
            description = "Another address (\"This invitation was sent to a different email address.\"), with"
                    + " invitedEmail, the invited address masked, such as j***@example.com",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "A link that doesn't work, or no longer applies (\"This invitation is no longer valid.\"),"
                    + " or sharing is off",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "410",
            description = "More than 14 days old (\"This invitation has expired.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    AcceptedInviteResponse accept(@AuthenticationPrincipal SignedInUser user, @PathVariable String token) {
        return new AcceptedInviteResponse(invites.accept(user, token));
    }

    /**
     * What an invitation link leads to.
     *
     * @param invite what the preview shows; left out unless the link works
     * @param state whether it works
     */
    record InvitePreviewResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable InviteSummary invite,

            @Schema(allowableValues = {"valid", "expired", "invalid"})
            String state) {}

    /**
     * What the preview shows of a shared copy.
     *
     * @param signable whether the person is asked to sign it, rather than only view it
     */
    record InviteSummary(String name, String ownerEmail, String ownerName, boolean signable) {}

    /** The answer to accepting: the shared copy to open. */
    record AcceptedInviteResponse(String sharedDocumentId) {}
}

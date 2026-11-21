package com.dokiments.sharing.adapter.in.web;

import com.dokiments.shared.SignedInUser;
import com.dokiments.sharing.application.port.in.ManageShares;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sharing documents for signature. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>Every route needs a session ({@code 401 Unauthorised.} otherwise), and answers {@code 404 Not found.} while
 * sharing is switched off ({@code DOCUMENT_SHARING_ENABLED}), as if it didn't exist. The body is checked after that, as
 * in the web app, so it isn't checked by Bean Validation.
 */
@RestController
@Tag(name = "Sharing")
class SharingController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageShares shares;

    SharingController(ManageShares shares) {
        this.shares = shares;
    }

    @PostMapping("/api/documents/{id}/share")
    @Operation(
            operationId = "shareDocument",
            summary = "Share a document for signature",
            description = "Shares a copy of the document with one person. Sharing again with the same person refreshes"
                    + " their copy (200); sharing with someone it was taken back from gives them access again"
                    + " (201). A document whose template has a recipient signature can be signed by them;"
                    + " any other is shared to view only.")
    @ApiResponse(responseCode = "201", description = "Shared with them, newly")
    @ApiResponse(responseCode = "200", description = "Refreshed for the person who has it")
    @ApiResponse(
            responseCode = "400",
            description = "Shared with oneself (\"You cannot share a document with yourself.\"), or an invalid body",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "403",
            description = "The plan's sharing limit (\"You have reached the sharing limit for your plan.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such document of the user's (\"Document not found.\"), or sharing is off",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "409",
            description = "Signed already (\"This document has been signed and can no longer be changed.\"), shared"
                    + " with someone else, or another request shared it at the same moment",
            content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<ShareResponse> share(
            @AuthenticationPrincipal SignedInUser user, @PathVariable String id, @RequestBody ShareRequestBody body) {
        var share = shares.share(user, id, body.email());
        var recipient = share.recipient();
        return ResponseEntity.status(share.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ShareResponse(
                        new ShareResponse.SharedWith(
                                recipient.email(), recipient.status().value()),
                        share.document().id()));
    }

    @GetMapping("/api/documents/share-recipients")
    @Operation(
            operationId = "listShareRecipients",
            summary = "List the people shared with",
            description = "Everyone the user has ever shared a document with, the most recent first, to suggest"
                    + " addresses.")
    @ApiResponse(responseCode = "200", description = "The people, an empty list for a user who has shared nothing")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "Sharing is off (\"Not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    LedgerResponse ledger(@AuthenticationPrincipal SignedInUser user) {
        return new LedgerResponse(shares.ledger(user).stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.email(),
                        entry.firstSharedAt().toEpochMilli(),
                        entry.lastSharedAt().toEpochMilli()))
                .toList());
    }

    // The query parameter is null when left out. It isn't marked @Nullable, because Spring's required = false already
    // says so to the IDE; it is only compared with "me".
    @GetMapping("/api/documents/shared")
    @Operation(
            operationId = "listSharedDocuments",
            summary = "List shared documents",
            description = "With by=me, the copies the user has shared; otherwise the copies shared with the user, which"
                    + " only an account with a verified address sees. The most recently changed first.")
    @ApiResponse(responseCode = "200", description = "The shared copies")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "Sharing is off (\"Not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    SharedDocumentsResponse list(
            @AuthenticationPrincipal SignedInUser user,
            @Parameter(description = "me for the copies the user has shared", schema = @Schema(allowableValues = "me"))
                    @RequestParam(required = false)
                    String by) {
        var documents = "me".equals(by) ? shares.sharedBy(user) : shares.sharedWith(user);
        return new SharedDocumentsResponse(
                documents.stream().map(SharedDocumentResponse::of).toList());
    }

    @GetMapping("/api/documents/shared/{id}")
    @Operation(
            operationId = "getSharedDocument",
            summary = "Open a shared document",
            description = "One shared copy, for its owner or the person it is shared with. A recipient opening it for"
                    + " the first time is noted, so the owner can see it was opened; that never changes the answer.")
    @ApiResponse(responseCode = "200", description = "The shared copy")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such copy, or not the user's to open (\"You do not have access to this shared"
                    + " document.\"), or sharing is off",
            content = @Content(schema = @Schema(ref = ERROR)))
    SharedDocumentEnvelope get(@AuthenticationPrincipal SignedInUser user, @PathVariable String id) {
        return new SharedDocumentEnvelope(SharedDocumentResponse.of(shares.open(user, id)));
    }

    @PostMapping("/api/documents/shared/{id}/sign")
    @Operation(
            operationId = "signSharedDocument",
            summary = "Sign a shared document",
            description = "Signs a copy shared with the user, with their typed name, and locks it so nobody can change"
                    + " it afterwards. Only a recipient can sign. The owner is emailed afterwards, which never changes"
                    + " the answer.")
    @ApiResponse(responseCode = "200", description = "Signed; the copy as it is now")
    @ApiResponse(
            responseCode = "400",
            description = "A missing, empty or over-long signature (an invalid body), or a copy shared to view, with"
                    + " nothing to sign (\"This document has no recipient signature. It was shared to view, so it"
                    + " cannot be signed.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "Not shared with the user, or no such copy (\"You do not have access to this shared"
                    + " document.\"), or sharing is off",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "409",
            description = "Signed already (\"This document has been signed and can no longer be changed.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    SharedDocumentEnvelope sign(
            @AuthenticationPrincipal SignedInUser user, @PathVariable String id, @RequestBody SignRequestBody body) {
        return new SharedDocumentEnvelope(SharedDocumentResponse.of(shares.sign(user, id, body.signature())));
    }

    @PostMapping("/api/documents/shared/{id}/revoke")
    @Operation(
            operationId = "revokeSharedDocument",
            summary = "Take a shared copy back",
            description = "As its owner, from the person it is shared with, who loses access. A signed copy can't be"
                    + " taken back.")
    @ApiResponse(responseCode = "200", description = "Taken back; the copy as it is now")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such copy of the user's (\"Document not found.\"), or sharing is off",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "409",
            description = "Signed already, or shared with nobody (\"This document is not shared with anyone.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    SharedDocumentEnvelope revoke(@AuthenticationPrincipal SignedInUser user, @PathVariable String id) {
        return new SharedDocumentEnvelope(SharedDocumentResponse.of(shares.revoke(user, id)));
    }

    /** Whom to share with, as typed. Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record ShareRequestBody(
            @Nullable @Schema(example = "client@acme.example")
            String email) {}

    /** The name a recipient types to sign. Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record SignRequestBody(
            @Nullable @Schema(example = "Jane Doe", maxLength = 200)
            String signature) {}

    /**
     * The answer to sharing.
     *
     * @param recipient whom it is shared with, and how far they have got
     * @param sharedDocumentId the shared copy's ID
     */
    record ShareResponse(SharedWith recipient, String sharedDocumentId) {

        /** The recipient, briefly. */
        record SharedWith(
                String email,

                @Schema(allowableValues = {"pending", "viewed", "signed", "revoked"})
                String status) {}
    }

    /** The answer to listing the people shared with: {@code { "recipients": [...] }}. */
    record LedgerResponse(List<LedgerEntryResponse> recipients) {}

    /**
     * Someone the user has shared with.
     *
     * @param firstSharedAt when the user first shared anything with them, in milliseconds since 1970
     * @param lastSharedAt when the user last shared something with them, in milliseconds since 1970
     */
    record LedgerEntryResponse(String email, long firstSharedAt, long lastSharedAt) {}

    /** The answer to listing shared copies: {@code { "documents": [...] }}. */
    record SharedDocumentsResponse(List<SharedDocumentResponse> documents) {}

    /** One shared copy: {@code { "document": {...} }}. */
    record SharedDocumentEnvelope(SharedDocumentResponse document) {}
}

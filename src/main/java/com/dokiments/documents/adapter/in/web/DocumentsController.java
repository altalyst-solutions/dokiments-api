package com.dokiments.documents.adapter.in.web;

import com.dokiments.documents.application.port.in.ManageDocuments;
import com.dokiments.documents.domain.DocumentChanges;
import com.dokiments.documents.domain.DocumentContent;
import com.dokiments.documents.domain.DocumentFilter;
import com.dokiments.shared.SignedInUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's documents. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>Every route needs a session ({@code 401 Unauthorised.} otherwise), and only ever reaches the signed-in user's own
 * documents: a document of someone else is answered like one that doesn't exist.
 */
@RestController
@Tag(name = "Documents")
class DocumentsController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageDocuments documents;

    DocumentsController(ManageDocuments documents) {
        this.documents = documents;
    }

    // The query parameter is null when left out. It isn't marked @Nullable, because Spring's required = false already
    // says so to the IDE; it is only handed to DocumentFilter.of, which accepts null.
    @GetMapping("/api/documents")
    @Operation(
            operationId = "listDocuments",
            summary = "List the documents",
            description = "The signed-in user's documents, in the order of their IDs, optionally only those made from"
                    + " one template.")
    @ApiResponse(responseCode = "200", description = "The documents, an empty list for a user who has none")
    @ApiResponse(
            responseCode = "400",
            description = "An empty templateId (\"Invalid query parameters.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    DocumentsResponse list(
            @AuthenticationPrincipal SignedInUser user,
            @Parameter(description = "Only documents made from this template, such as classic-invoice")
                    @RequestParam(required = false)
                    String templateId) {
        return new DocumentsResponse(documents.list(user.uid(), DocumentFilter.of(templateId)).stream()
                .map(DocumentResponse::of)
                .toList());
    }

    @PostMapping("/api/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createDocument",
            summary = "Make a document",
            description = "From a template of the catalog. The document keeps a copy of the template as it is now"
                    + " (templateSnapshot), so later changes to the catalog never change it. The name is 1 to 200"
                    + " characters; values holds at most 50 texts of at most 20,000 characters each.")
    @ApiResponse(responseCode = "201", description = "Made, with its new ID")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "The template isn't in the catalog (\"Template not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    DocumentEnvelope create(@AuthenticationPrincipal SignedInUser user, @Valid @RequestBody NewDocumentRequest body) {
        var document = documents.create(
                user.uid(),
                body.templateId(),
                DocumentContent.name(body.name()),
                DocumentContent.values(body.values()));
        return new DocumentEnvelope(DocumentResponse.of(document));
    }

    @GetMapping("/api/documents/{id}")
    @Operation(operationId = "getDocument", summary = "Get a document")
    @ApiResponse(responseCode = "200", description = "The document")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such document among the user's own (\"Document not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    DocumentEnvelope get(@AuthenticationPrincipal SignedInUser user, @PathVariable String id) {
        return new DocumentEnvelope(DocumentResponse.of(documents.get(user.uid(), id)));
    }

    @PatchMapping("/api/documents/{id}")
    @Operation(
            operationId = "changeDocument",
            summary = "Change a document",
            description = "Its name, its values, or both; at least one is needed. The values are replaced as a whole."
                    + " The template it was made from never changes. The answer is the whole document as stored.")
    @ApiResponse(responseCode = "200", description = "Changed")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such document among the user's own (\"Document not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    DocumentEnvelope change(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable String id,
            @Valid @RequestBody DocumentChangesRequest body) {
        var changes = DocumentChanges.of(body.name(), body.values());
        return new DocumentEnvelope(DocumentResponse.of(documents.change(user.uid(), id, changes)));
    }

    @DeleteMapping("/api/documents/{id}")
    @Operation(
            operationId = "removeDocument",
            summary = "Remove a document",
            description = "Also 204 when there is" + " no such document.")
    @ApiResponse(responseCode = "204", description = "Removed, or not there")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<Void> remove(@AuthenticationPrincipal SignedInUser user, @PathVariable String id) {
        documents.remove(user.uid(), id);
        return ResponseEntity.noContent().build();
    }

    /** A new document. Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record NewDocumentRequest(
            @NotNull @Schema(example = "Invoice for Acme") String name,
            @NotEmpty @Schema(example = "classic-invoice") String templateId,
            @NotNull Map<String, @Nullable String> values) {}

    /** The change. A field left out, or sent as {@code null}, stays as it is. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record DocumentChangesRequest(
            @Nullable String name, @Nullable Map<String, @Nullable String> values) {}

    /** The answer to listing: {@code { "documents": [...] }}. */
    record DocumentsResponse(List<DocumentResponse> documents) {}

    /** The answer for one document: {@code { "document": {...} }}. */
    record DocumentEnvelope(DocumentResponse document) {}
}

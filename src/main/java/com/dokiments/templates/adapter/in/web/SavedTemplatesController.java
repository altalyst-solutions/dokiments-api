package com.dokiments.templates.adapter.in.web;

import com.dokiments.shared.SignedInUser;
import com.dokiments.templates.application.port.in.ManageSavedTemplates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's library of saved templates. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>Every route needs a session ({@code 401 Unauthorised.} otherwise), and only ever reaches the signed-in user's own
 * library.
 */
@RestController
@Tag(name = "Saved templates")
class SavedTemplatesController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageSavedTemplates savedTemplates;

    SavedTemplatesController(ManageSavedTemplates savedTemplates) {
        this.savedTemplates = savedTemplates;
    }

    @GetMapping("/api/saved-templates")
    @Operation(
            operationId = "listSavedTemplates",
            summary = "List the saved templates",
            description = "Each with its template from the catalog, in the order of their template IDs. A saved"
                    + " template whose template is no longer in the catalog is left out.")
    @ApiResponse(responseCode = "200", description = "The saved templates, an empty list for a user who has none")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    SavedTemplatesResponse list(@AuthenticationPrincipal SignedInUser user) {
        return new SavedTemplatesResponse(savedTemplates.list(user.uid()).stream()
                .map(entry -> new SavedTemplateEntry(
                        entry.saved().savedAt().toEpochMilli(),
                        TemplateResponse.of(entry.template()),
                        entry.saved().templateId()))
                .toList());
    }

    @PostMapping("/api/saved-templates")
    @Operation(
            operationId = "saveTemplate",
            summary = "Save a template",
            description = "Adds a template of the catalog to the library. Saving one that is saved already answers 200"
                    + " and only updates savedAt. The plan decides which templates may be saved, and a free account"
                    + " keeps at most two.")
    @ApiResponse(responseCode = "201", description = "Added")
    @ApiResponse(responseCode = "200", description = "Saved already; savedAt updated")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "403",
            description = "Not in the plan (\"Your plan does not include this template.\"), or a free account at its"
                    + " limit (\"You've reached your saved template limit.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "Not in the catalog (\"Template not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<SavedResponse> save(
            @AuthenticationPrincipal SignedInUser user, @Valid @RequestBody SaveRequest body) {
        var result = savedTemplates.save(user.uid(), body.templateId());
        var saved = result.saved();
        return ResponseEntity.status(result.added() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new SavedResponse(saved.savedAt().toEpochMilli(), saved.templateId(), saved.templateId()));
    }

    @DeleteMapping("/api/saved-templates/{templateId}")
    @Operation(
            operationId = "removeSavedTemplate",
            summary = "Remove a saved template",
            description = "Also 204 when it isn't saved.")
    @ApiResponse(responseCode = "204", description = "Removed, or not there")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<Void> remove(@AuthenticationPrincipal SignedInUser user, @PathVariable String templateId) {
        savedTemplates.remove(user.uid(), templateId);
        return ResponseEntity.noContent().build();
    }

    /** The template to save. Other fields are ignored, as in the web app. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record SaveRequest(
            @NotEmpty @Schema(example = "classic-invoice") String templateId) {}

    /** The library: {@code { "savedTemplates": [...] }}. */
    record SavedTemplatesResponse(List<SavedTemplateEntry> savedTemplates) {}

    /**
     * A saved template with its template.
     *
     * @param savedAt when it was last saved, in milliseconds since 1970
     */
    record SavedTemplateEntry(long savedAt, TemplateResponse template, String templateId) {}

    /**
     * The answer to saving, as the web app gives it.
     *
     * @param savedAt when it was saved, in milliseconds since 1970
     * @param savedId the saved template's ID, which is always the template's
     */
    record SavedResponse(long savedAt, String savedId, String templateId) {}
}

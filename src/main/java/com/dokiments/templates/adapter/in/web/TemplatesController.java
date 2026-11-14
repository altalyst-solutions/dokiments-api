package com.dokiments.templates.adapter.in.web;

import com.dokiments.templates.application.port.in.BrowseTemplates;
import com.dokiments.templates.domain.CatalogFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The template catalog. Paths, bodies, status codes and messages are the web app's. It is the same for every user, but
 * only shown to signed-in ones ({@code 401 Unauthorised.} otherwise), as in the web app.
 */
@RestController
@Tag(name = "Templates")
class TemplatesController {

    private static final String ERROR = "#/components/schemas/Error";

    private final BrowseTemplates templates;

    TemplatesController(BrowseTemplates templates) {
        this.templates = templates;
    }

    // The optional query parameters are null when left out. They aren't marked @Nullable, because Spring's
    // required = false already says so to the IDE; they are only handed to CatalogFilter.of, which accepts null.
    @GetMapping("/api/templates")
    @Operation(
            operationId = "listTemplates",
            summary = "List the catalog",
            description = "Every active style, unfiltered, for the style tabs, and the active templates, optionally"
                    + " only those of one style or tier. Both in their intended order.")
    @ApiResponse(responseCode = "200", description = "The styles and templates")
    @ApiResponse(
            responseCode = "400",
            description = "An unknown style or tier (\"Invalid query parameters.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    CatalogResponse list(
            @Parameter(schema = @Schema(allowableValues = {"classic", "modern", "brutalist", "minimalist"}))
                    @RequestParam(required = false)
                    String styleId,
            @Parameter(schema = @Schema(allowableValues = {"free", "silver", "gold"})) @RequestParam(required = false)
                    String tier) {
        var listing = templates.list(CatalogFilter.of(styleId, tier));
        return new CatalogResponse(
                listing.styles().stream().map(StyleResponse::of).toList(),
                listing.templates().stream().map(TemplateResponse::of).toList());
    }

    @GetMapping("/api/templates/{id}")
    @Operation(
            operationId = "getTemplate",
            summary = "Get a template",
            description = "One template of the catalog, with its style, to resolve the template of a saved template or"
                    + " a document.")
    @ApiResponse(responseCode = "200", description = "The template")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "Not in the catalog, or hidden (\"Template not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    TemplateEnvelope get(@PathVariable String id) {
        return new TemplateEnvelope(TemplateResponse.of(templates.get(id)));
    }

    /** The catalog: {@code { "styles": [...], "templates": [...] }}. */
    record CatalogResponse(List<StyleResponse> styles, List<TemplateResponse> templates) {}

    /** One template: {@code { "template": {...} }}. */
    record TemplateEnvelope(TemplateResponse template) {}
}

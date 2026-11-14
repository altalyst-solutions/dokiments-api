package com.dokiments.clients.adapter.in.web;

import com.dokiments.clients.application.port.in.ManageClients;
import com.dokiments.clients.domain.Client;
import com.dokiments.clients.domain.ClientChanges;
import com.dokiments.clients.domain.ClientDetails;
import com.dokiments.shared.SignedInUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's clients. Paths, bodies, status codes and messages are the web app's.
 *
 * <p>Every route needs a session ({@code 401 Unauthorised.} otherwise), and only ever reaches the signed-in user's own
 * clients: a client of someone else is answered like one that doesn't exist.
 */
@RestController
@Tag(name = "Clients")
class ClientsController {

    private static final String ERROR = "#/components/schemas/Error";

    private final ManageClients clients;

    ClientsController(ManageClients clients) {
        this.clients = clients;
    }

    @GetMapping("/api/clients")
    @Operation(
            summary = "List the clients",
            description = "The signed-in user's clients, the most recently added first.")
    @ApiResponse(responseCode = "200", description = "The clients, an empty list for a user who has none")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    ClientsResponse list(@AuthenticationPrincipal SignedInUser user) {
        return new ClientsResponse(
                clients.list(user.uid()).stream().map(ClientResponse::of).toList());
    }

    @PostMapping("/api/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a client",
            description = "Every field is trimmed. The name, company name, email and phone are required; a missing"
                    + " address, BRN or national ID is stored as empty text.")
    @ApiResponse(responseCode = "201", description = "Added, with its new ID")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    ClientEnvelope add(@AuthenticationPrincipal SignedInUser user, @Valid @RequestBody NewClientRequest body) {
        var details = ClientDetails.of(
                orEmpty(body.address()),
                orEmpty(body.brn()),
                body.companyName(),
                body.email(),
                body.name(),
                orEmpty(body.nationalId()),
                body.phone());
        return new ClientEnvelope(ClientResponse.of(clients.add(user.uid(), details)));
    }

    @PatchMapping("/api/clients/{clientId}")
    @Operation(
            summary = "Change a client",
            description = "Only the fields sent change; at least one is needed. They follow the same rules as for a"
                    + " new client. The answer is the whole client as changed.")
    @ApiResponse(responseCode = "200", description = "Changed")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    @ApiResponse(
            responseCode = "404",
            description = "No such client among the user's own (\"Client not found.\")",
            content = @Content(schema = @Schema(ref = ERROR)))
    ClientEnvelope change(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable String clientId,
            @Valid @RequestBody ClientChangesRequest body) {
        var changes = ClientChanges.of(
                body.address(),
                body.brn(),
                body.companyName(),
                body.email(),
                body.name(),
                body.nationalId(),
                body.phone());
        return new ClientEnvelope(ClientResponse.of(clients.change(user.uid(), clientId, changes)));
    }

    @DeleteMapping("/api/clients/{clientId}")
    @Operation(
            summary = "Remove a client",
            description = "Also 204 when there is no such client. Documents already prepared for the client keep its"
                    + " details.")
    @ApiResponse(responseCode = "204", description = "Removed, or not there")
    @ApiResponse(responseCode = "401", description = "Not signed in", content = @Content(schema = @Schema(ref = ERROR)))
    ResponseEntity<Void> remove(@AuthenticationPrincipal SignedInUser user, @PathVariable String clientId) {
        clients.remove(user.uid(), clientId);
        return ResponseEntity.noContent().build();
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /**
     * A new client. Other fields are ignored, as in the web app, and an optional field sent as {@code null} counts as
     * left out.
     */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record NewClientRequest(
            @Nullable String address,
            @Nullable String brn,
            @NotNull @Schema(example = "Acme Ltd") String companyName,
            @NotNull @Schema(example = "jane@acme.example") String email,
            @NotNull @Schema(example = "Jane Doe") String name,
            @Nullable String nationalId,
            @NotNull @Schema(example = "+230 5123 4567") String phone) {}

    /** The fields to change. A field left out, or sent as {@code null}, stays as it is. */
    @SuppressWarnings("unused") // Created by Jackson from the request body.
    record ClientChangesRequest(
            @Nullable String address,
            @Nullable String brn,
            @Nullable String companyName,
            @Nullable String email,
            @Nullable String name,
            @Nullable String nationalId,
            @Nullable String phone) {}

    /** The answer to listing: {@code { "clients": [...] }}. */
    record ClientsResponse(List<ClientResponse> clients) {}

    /** The answer to adding or changing: {@code { "client": {...} }}. */
    record ClientEnvelope(ClientResponse client) {}

    /**
     * A client as the web app shows it, with the times in milliseconds since 1970.
     *
     * @param createdAt when the client was added, {@code 0} for an older client without the time
     * @param updatedAt when the details last changed, the same as {@code createdAt} until then
     */
    record ClientResponse(
            String address,
            String brn,
            String companyName,
            long createdAt,
            String email,
            @Schema(example = "client_mhx2k1q8_4f9a2c") String id,
            String name,
            String nationalId,
            String phone,
            long updatedAt) {

        static ClientResponse of(Client client) {
            var details = client.details();
            return new ClientResponse(
                    details.address(),
                    details.brn(),
                    details.companyName(),
                    client.createdAt().toEpochMilli(),
                    details.email(),
                    client.id().value(),
                    details.name(),
                    details.nationalId(),
                    details.phone(),
                    client.updatedAt().toEpochMilli());
        }
    }
}

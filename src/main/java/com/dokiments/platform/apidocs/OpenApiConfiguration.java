package com.dokiments.platform.apidocs;

import com.dokiments.platform.security.CsrfExemptions;
import com.dokiments.platform.web.ErrorMessages;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the API description says about the API as a whole, including the error responses every endpoint shares.
 *
 * <p>Errors are described here once instead of with annotations on every controller: every error has the same {@code {
 * "error": "..." }} body (see {@code platform.web.ErrorBody}), and which ones an endpoint can answer follows from what
 * it is. A controller only annotates the errors that are specific to it, such as a {@code 404} for a document that
 * doesn't exist.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String ERROR_SCHEMA = "Error";

    @Bean
    OpenAPI dokimentsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dokiments API")
                        .description("The backend of the Dokiments web app, which forwards its /api/* requests here."
                                + " Every error answers with { \"error\": \"<message>\" }, a message safe to show"
                                + " to the user.")
                        // The version of the contract, not of the application: it changes by hand when the contract
                        // does, so docs/openapi.yaml doesn't change with every build.
                        .version("1"))
                // Relative, so Swagger UI calls the server it was opened from, whatever its address.
                .servers(List.of(new Server().url("/")));
    }

    /** {@code { "error": "<message>" }}, as {@code platform.web.ErrorBody} writes it. */
    static Schema<Object> errorSchema() {
        Schema<Object> schema = new ObjectSchema();
        schema.setDescription("The body of every error response.");
        schema.addProperty("error", new StringSchema().description("A message safe to show to the user."));
        schema.addProperty(
                "code",
                new StringSchema()
                        .description("Only for failures the web app handles in code, such as REAUTH_REQUIRED."));
        schema.setRequired(List.of("error"));
        return schema;
    }

    /** Adds the error schema, and the errors every endpoint can answer to every operation. */
    @Bean
    OpenApiCustomizer sharedErrorResponses() {
        return openApi -> {
            // Added here rather than on the OpenAPI bean: springdoc builds the schemas from the controllers and
            // replaces any set there, which left every error response pointing at a schema that wasn't in the file.
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, item) -> {
                    item.readOperations().forEach(OpenApiConfiguration::addErrors);
                    if (!CsrfExemptions.PATHS.contains(path)) {
                        Stream.of(item.getPost(), item.getPut(), item.getPatch(), item.getDelete())
                                .filter(Objects::nonNull)
                                .forEach(OpenApiConfiguration::addCsrfError);
                    }
                });
            }
        };
    }

    static void addErrors(Operation operation) {
        ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        if (operation.getRequestBody() != null) {
            addIfAbsent(
                    responses,
                    "400",
                    "The body isn't valid JSON (\"" + ErrorMessages.INVALID_JSON + "\") or doesn't match what the"
                            + " endpoint expects (\"" + ErrorMessages.INVALID_BODY + "\").");
        }
        addIfAbsent(responses, "500", "An unexpected failure: \"" + ErrorMessages.UNEXPECTED + "\"");
        addIfAbsent(
                responses,
                "503",
                "An external service, such as Firestore, couldn't be reached: \"" + ErrorMessages.UPSTREAM_UNAVAILABLE
                        + "\"");
        operation.setResponses(responses);
    }

    /** Requests that change something are refused without the CSRF token (see {@code platform.security}). */
    static void addCsrfError(Operation operation) {
        addIfAbsent(
                operation.getResponses(),
                "403",
                "The CSRF token is missing or wrong: send the value of the XSRF-TOKEN cookie in the X-XSRF-TOKEN"
                        + " header (\"" + ErrorMessages.FORBIDDEN + "\").");
    }

    /** Keeps a controller's own description of the same status, which is more specific. */
    private static void addIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, errorResponse(description));
        }
    }

    private static ApiResponse errorResponse(String description) {
        Schema<?> error = new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA);
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(error)));
    }
}

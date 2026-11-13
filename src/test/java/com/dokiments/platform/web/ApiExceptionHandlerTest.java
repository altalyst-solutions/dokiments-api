package com.dokiments.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dokiments.shared.error.ConflictException;
import com.dokiments.shared.error.ForbiddenException;
import com.dokiments.shared.error.InvalidRequestException;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.shared.error.TooManyRequestsException;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.shared.error.UpstreamUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/**
 * Checks that every kind of failure is answered with the status code and exact body the web app expects.
 *
 * <p>Bodies are compared strictly, so a response with any extra field (a timestamp, a path, an exception name) fails.
 * Security filters are left out here: the {@code 401} and {@code 403} written by Spring Security are checked in
 * {@code ApplicationIT}.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.ProbeController.class)
// Spring Boot's test scanning skips classes nested in test classes, so the probe controller is imported explicitly.
@Import({ApiExceptionHandlerTest.ProbeController.class, StrictJsonConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest
    @CsvSource({
        "invalid-request, 400, The email address is not valid.",
        "unauthenticated, 401, Unauthorised.",
        "forbidden, 403, Your plan does not allow this action.",
        "not-found, 404, Document not found.",
        "conflict, 409, This document is not shared with anyone.",
        "upstream-unavailable, 503, Couldn't reach an external service. Please try again in a moment.",
        "upstream-io, 503, Couldn't reach an external service. Please try again in a moment.",
        "too-many, 429, Too many attempts. Please try again later.",
    })
    void applicationExceptionsKeepTheirMessageAndGetTheirStatus(String path, int status, String message) {
        assertThat(mvc.get().uri("/probe/fail/{path}", path))
                .hasStatus(status)
                .bodyJson()
                .isStrictlyEqualTo(body(message));
    }

    @Test
    void aCodeTheWebAppActsOnIsSentNextToTheMessage() {
        assertThat(mvc.get().uri("/probe/fail/with-code"))
                .hasStatus(403)
                .bodyJson()
                .isStrictlyEqualTo("{\"code\":\"REAUTH_REQUIRED\",\"error\":\"Sign in again first.\"}");
    }

    @Test
    void unexpectedErrorsAreHiddenBehindAGenericMessage() {
        assertThat(mvc.get().uri("/probe/fail/bug"))
                .hasStatus(500)
                .bodyJson()
                .isStrictlyEqualTo(body("Something went wrong. Please try again."));
    }

    @Test
    void anUnexpectedErrorNeverLeaksItsDetails() {
        assertThat(mvc.get().uri("/probe/fail/bug"))
                .bodyText()
                .doesNotContain("internal detail", "IllegalStateException");
    }

    @Test
    void malformedJsonIsReportedAsInvalidJson() {
        assertThat(mvc.post()
                        .uri("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo(body("Request body must be valid JSON."));
    }

    @Test
    void aMissingBodyIsReportedAsInvalidJson() {
        assertThat(mvc.post().uri("/probe/body").contentType(MediaType.APPLICATION_JSON))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo(body("Request body must be valid JSON."));
    }

    @Test
    void validJsonWithAWrongTypeIsReportedAsAnInvalidBody() {
        assertThat(mvc.post()
                        .uri("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"count\":\"many\"}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo(body("Invalid request body."));
    }

    @Test
    void aNumberOrBooleanWhereTextIsExpectedIsAnInvalidBody() {
        for (String email : new String[] {"5", "1.5", "true"}) {
            assertThat(mvc.post()
                            .uri("/probe/body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": " + email + ", \"count\": 1}"))
                    .as(email)
                    .hasStatus(400)
                    .bodyJson()
                    .isStrictlyEqualTo(body("Invalid request body."));
        }
    }

    @Test
    void aBodyThatFailsValidationIsReportedAsAnInvalidBody() {
        assertThat(mvc.post()
                        .uri("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"count\":1}"))
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo(body("Invalid request body."));
    }

    @Test
    void aValidBodyIsAccepted() {
        assertThat(mvc.post()
                        .uri("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"count\":1}"))
                .hasStatusOk();
    }

    @ParameterizedTest
    @CsvSource({
        "/probe/query, missing parameter",
        "/probe/query?page=abc, wrong type",
        "/probe/query?page=0, fails validation",
    })
    void badQueryParametersAreReportedAsInvalidQueryParameters(String uri, String reason) {
        assertThat(mvc.get().uri(uri))
                .as(reason)
                .hasStatus(400)
                .bodyJson()
                .isStrictlyEqualTo(body("Invalid query parameters."));
    }

    @Test
    void anUnknownPathIsNotFound() {
        assertThat(mvc.get().uri("/probe/no/such/path"))
                .hasStatus(404)
                .bodyJson()
                .isStrictlyEqualTo(body("Not found."));
    }

    @Test
    void anUnsupportedMethodSaysWhichMethodsAreAllowed() {
        assertThat(mvc.delete().uri("/probe/body"))
                .hasStatus(405)
                .hasHeader(HttpHeaders.ALLOW, "POST")
                .bodyJson()
                .isStrictlyEqualTo(body("Method not allowed."));
    }

    @Test
    void answersAreJsonEvenWhenAClientAsksForXml() {
        // What a browser sends when it opens an address directly: XML ranks above anything else.
        assertThat(mvc.get()
                        .uri("/probe/fail/not-found")
                        .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
                .hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isStrictlyEqualTo(body("Document not found."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/yaml", "application/cbor", "application/x-jackson-smile"})
    void otherDataFormatsAreNeverProduced(String format) {
        assertThat(mvc.get().uri("/probe/query?page=1").header(HttpHeaders.ACCEPT, format + ",*/*;q=0.1"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @Test
    void anUnsupportedContentTypeIsRejected() {
        assertThat(mvc.post()
                        .uri("/probe/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .hasStatus(415)
                .bodyJson()
                .isStrictlyEqualTo(body("Unsupported content type."));
    }

    private static String body(String message) {
        return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }

    // Created by Jackson from the request body, which the IDE can't see.
    @SuppressWarnings("unused")
    record ProbeBody(@NotBlank String email, int count) {}

    /** Test-only endpoints that fail in every way the handler has to cover. */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/fail/{kind}")
        String fail(@PathVariable String kind) {
            throw switch (kind) {
                case "invalid-request" -> new InvalidRequestException("The email address is not valid.");
                case "unauthenticated" -> new UnauthenticatedException();
                case "forbidden" -> new ForbiddenException("Your plan does not allow this action.");
                case "not-found" -> new NotFoundException("Document not found.");
                case "conflict" -> new ConflictException("This document is not shared with anyone.");
                case "upstream-unavailable" -> new UpstreamUnavailableException(new ConnectException("refused"));
                case "too-many" -> new TooManyRequestsException("Too many attempts. Please try again later.", null);
                case "with-code" ->
                    new ForbiddenException("Sign in again first.") {
                        @Override
                        public String publicCode() {
                            return "REAUTH_REQUIRED";
                        }
                    };
                case "upstream-io" ->
                    new ResourceAccessException("I/O error on GET request for \"https://x.test?key=k\"");
                default -> new IllegalStateException("internal detail that must not reach the client");
            };
        }

        // Only the validation matters here; echoing a field back keeps the handler meaningful.
        @PostMapping("/probe/body")
        String acceptBody(@Valid @RequestBody ProbeBody body) {
            return body.email();
        }

        @GetMapping("/probe/query")
        int acceptQuery(@RequestParam @Min(1) int page) {
            return page;
        }
    }
}

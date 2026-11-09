package com.dokiments.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/** Checks the body of errors answered by Spring Boot's {@code /error} page, outside any controller. */
class ApiErrorAttributesTest {

    private final ApiErrorAttributes errorAttributes = new ApiErrorAttributes();

    @ParameterizedTest
    @CsvSource({
        "400, Invalid request body.",
        "401, Unauthorised.",
        "403, Forbidden.",
        "404, Not found.",
        "500, Something went wrong. Please try again.",
        "503, Couldn't reach an external service. Please try again in a moment.",
    })
    void onlyTheGenericMessageForTheStatusIsReturned(int status, String message) {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("internal detail"));

        var attributes = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request),
                ErrorAttributeOptions.of(
                        ErrorAttributeOptions.Include.MESSAGE,
                        ErrorAttributeOptions.Include.EXCEPTION,
                        ErrorAttributeOptions.Include.STACK_TRACE));

        // Even when asked to include details, nothing but the generic message comes back.
        assertThat(attributes).containsExactlyEntriesOf(java.util.Map.of("error", message));
    }
}

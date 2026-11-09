package com.dokiments.platform.web;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * The body of errors that happen outside any controller, so {@link ApiExceptionHandler} never sees them: for example an
 * exception in a filter, or a {@code sendError} from the servlet container.
 *
 * <p>Spring Boot answers those through its {@code /error} endpoint, whose default body has a timestamp, path, status
 * and error name. This replaces it with the API's own format, {@code { "error": "..." }}, using the generic message for
 * the status code. The real error is still logged by Spring Boot; it is never returned.
 */
@Component
class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, @Nullable Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Object status = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, WebRequest.SCOPE_REQUEST);
        int statusCode = status instanceof Integer code ? code : 500;
        return Map.of("error", ErrorMessages.forStatus(statusCode));
    }
}

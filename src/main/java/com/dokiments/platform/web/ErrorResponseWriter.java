package com.dokiments.platform.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an error response directly to the servlet response, for code that runs outside Spring MVC and so can't return
 * a {@code ResponseEntity}, such as Spring Security's filters.
 *
 * <p>Uses the application's own {@link JsonMapper} and {@link ErrorBody}, so the body is exactly the same as for errors
 * from controllers.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), new ErrorBody(message));
    }
}

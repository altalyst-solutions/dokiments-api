package com.dokiments.platform.web;

import com.dokiments.shared.error.ConflictException;
import com.dokiments.shared.error.DokimentsException;
import com.dokiments.shared.error.ForbiddenException;
import com.dokiments.shared.error.InvalidRequestException;
import com.dokiments.shared.error.NotFoundException;
import com.dokiments.shared.error.TooManyRequestsException;
import com.dokiments.shared.error.UnauthenticatedException;
import com.dokiments.shared.error.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.DatabindException;

/**
 * Turns every exception thrown while handling a request into the API's error format, {@code { "error": "..." }}, with
 * the status code and wording the web app expects.
 *
 * <ul>
 *   <li>The application's own exceptions ({@link DokimentsException}) carry a message that is safe to show, and are
 *       mapped to their status code by type.
 *   <li>Spring's exceptions for bad input (malformed JSON, failed validation, wrong parameter types) become {@code 400}
 *       with a generic message. What exactly was wrong is logged, never returned, so the response can't reveal how an
 *       endpoint validates its input.
 *   <li>Anything else is a bug or an unexpected failure: it is logged in full and answered with a generic {@code 500},
 *       so internal details never reach the client.
 * </ul>
 *
 * <p>Errors that happen outside controllers, such as in a filter, are handled by {@link ApiErrorAttributes}, and Spring
 * Security's {@code 401} and {@code 403} by the handlers in the security package. All of them produce the same body.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DokimentsException.class)
    ResponseEntity<ErrorBody> handleDokimentsException(DokimentsException exception) {
        // Sealed hierarchy: the compiler checks that every kind of failure has a status code.
        HttpStatus status = switch (exception) {
            case InvalidRequestException _ -> HttpStatus.BAD_REQUEST;
            case UnauthenticatedException _ -> HttpStatus.UNAUTHORIZED;
            case ForbiddenException _ -> HttpStatus.FORBIDDEN;
            case NotFoundException _ -> HttpStatus.NOT_FOUND;
            case ConflictException _ -> HttpStatus.CONFLICT;
            case TooManyRequestsException _ -> HttpStatus.TOO_MANY_REQUESTS;
            case UpstreamUnavailableException _ -> HttpStatus.SERVICE_UNAVAILABLE;
        };

        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            // Reported to the client as "try again", but an outage still needs investigating.
            log.warn("External service unavailable: {}", exception.publicMessage(), exception);
        } else {
            log.debug(
                    "{} answered with {}: {}",
                    exception.getClass().getSimpleName(),
                    status.value(),
                    exception.publicMessage());
        }
        return ResponseEntity.status(status).body(new ErrorBody(exception.publicMessage(), exception.publicCode()));
    }

    /**
     * The body couldn't be read. If it isn't JSON at all (or is missing), the message says so; if it is JSON but a
     * field has the wrong type, it counts as an invalid body, the same as a failed validation.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> handleUnreadableBody(HttpMessageNotReadableException exception) {
        boolean validJsonWithWrongShape = exception.getCause() instanceof DatabindException;
        log.debug("Unreadable request body: {}", exception.getMessage());
        return respond(
                HttpStatus.BAD_REQUEST,
                validJsonWithWrongShape ? ErrorMessages.INVALID_BODY : ErrorMessages.INVALID_JSON);
    }

    /** A {@code @Valid @RequestBody} failed Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException exception) {
        log.debug(
                "Request body validation failed: {}",
                exception.getBindingResult().getAllErrors());
        return respond(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_BODY);
    }

    /**
     * Validation on the method's parameters failed. That can be the body or a query parameter, and the message says
     * which, as the web app does.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorBody> handleInvalidParameters(HandlerMethodValidationException exception) {
        boolean bodyIsInvalid = exception.getParameterValidationResults().stream()
                .anyMatch(result -> result.getMethodParameter().hasParameterAnnotation(RequestBody.class));
        log.debug("Request parameter validation failed: {}", exception.getParameterValidationResults());
        return respond(
                HttpStatus.BAD_REQUEST, bodyIsInvalid ? ErrorMessages.INVALID_BODY : ErrorMessages.INVALID_QUERY);
    }

    /** A query or path parameter is missing or can't be converted to its type. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> handleBadParameter(Exception exception) {
        log.debug("Invalid request parameter: {}", exception.getMessage());
        return respond(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_QUERY);
    }

    // Nothing to read from the exception, so the method doesn't take it as a parameter.
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> handleNoResource() {
        return respond(HttpStatus.NOT_FOUND, ErrorMessages.NOT_FOUND);
    }

    /** Keeps the {@code Allow} header, so the client can see which methods the path does support. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .body(new ErrorBody(ErrorMessages.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorBody> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(exception.getHeaders())
                .body(new ErrorBody(ErrorMessages.UNSUPPORTED_MEDIA_TYPE));
    }

    /**
     * A call to an external service failed on the network outside the checks in {@code platform.http}. Calls made
     * through Spring Boot's {@code RestClient.Builder} normally report a service that can't be reached as
     * {@link UpstreamUnavailableException} already. This covers the rest, for example a response body that an adapter
     * streams and that stops arriving, so such a failure is still answered with {@code 503} rather than {@code 500}.
     *
     * <p>Only the cause is logged: this exception's own message contains the full address, query included, which can
     * hold a key.
     */
    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ErrorBody> handleUpstreamIoFailure(ResourceAccessException exception) {
        log.warn("Call to an external service failed", exception.getCause());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, UpstreamUnavailableException.DEFAULT_MESSAGE);
    }

    /**
     * Everything else.
     *
     * <p>Many of Spring's own exceptions already know their status code (for example {@code 406} or {@code 413}): that
     * status is kept, with the generic message for it. Anything without one is a bug or an unexpected failure: it is
     * logged in full and answered with {@code 500}, never shown to the client.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse springError) {
            int status = springError.getStatusCode().value();
            if (status >= 500) {
                log.error("Request failed with {}", status, exception);
            } else {
                log.debug("Spring rejected the request with {}: {}", status, exception.getMessage());
            }
            HttpHeaders headers = springError.getHeaders();
            return ResponseEntity.status(status).headers(headers).body(new ErrorBody(ErrorMessages.forStatus(status)));
        }

        log.error("Unexpected error while handling a request", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.UNEXPECTED);
    }

    private static ResponseEntity<ErrorBody> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(message));
    }
}

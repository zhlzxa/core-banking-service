package io.github.zhlzxa.corebanking.web;

import io.github.zhlzxa.corebanking.common.error.BusinessException;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions raised while handling a request into RFC 9457 problem responses.
 *
 * <p>Every error body carries a stable {@code code} property that clients can branch on, and the
 * request's {@code correlationId} for support enquiries. Details are fixed sentences: exception
 * messages from infrastructure, SQL, stack traces and echoed client input never reach the
 * response. The complete catalogue is documented in {@code docs/api-errors.md}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        return respond(ProblemDetails.of(ex.errorCode(), ex.getMessage()));
    }

    /**
     * Method-level authorization failures are raised inside the controller call and would otherwise
     * be caught by the generic handler below and reported as 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(
                ProblemDetails.of(ErrorCode.ACCESS_DENIED, "The caller is not permitted to perform this operation"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "Authentication is required"));
    }

    /** Last resort: the cause is logged with the correlation id, the client only learns that it failed. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return respond(ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error))
                .toList();
        return validationFailed(errors);
    }

    /** Violations of constraints declared on handler method parameters, such as query parameters. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.add(fieldError(name, error)));
        });
        return validationFailed(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, "The request body could not be read");
        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /**
     * Completes the problem bodies Spring MVC creates for protocol-level errors such as 404, 405 or
     * 415. Those use the HTTP status name as their code.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                HttpStatus resolved = HttpStatus.resolve(statusCode.value());
                problem.setProperty("code", resolved != null ? resolved.name() : "HTTP_" + statusCode.value());
            }
            ProblemDetails.withCorrelationId(problem);
        }
        return response;
    }

    /** Only the constraint's generic message is returned; the rejected value is never echoed. */
    private static Map<String, String> fieldError(@Nullable String field, MessageSourceResolvable error) {
        return Map.of("field", String.valueOf(field), "reason", String.valueOf(error.getDefaultMessage()));
    }

    private static ResponseEntity<Object> validationFailed(List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.status(body.getStatus()).body(body);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}

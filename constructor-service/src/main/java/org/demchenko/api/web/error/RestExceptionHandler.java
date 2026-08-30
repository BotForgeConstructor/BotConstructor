package org.demchenko.api.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.api.generated.model.ApiError;
import org.demchenko.api.web.trace.CorrelationIdFilter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class RestExceptionHandler {
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;
    private final ApiErrorMapper mapper;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return validationResponse(exception.getBindingResult().getFieldErrors(), request);
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> handleBinding(BindException exception, HttpServletRequest request) {
        return validationResponse(exception.getBindingResult().getFieldErrors(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintValidation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<RestFieldError> fields = exception.getConstraintViolations().stream()
                .map(violation -> new RestFieldError(
                        violation.getPropertyPath().toString(),
                        safeValidationMessage(violation.getMessage())))
                .sorted(fieldComparator())
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedJson(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed", List.of(), request);
    }

    @ExceptionHandler(TypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(TypeMismatchException exception, HttpServletRequest request) {
        String path = exception.getPropertyName() == null ? "request" : exception.getPropertyName();
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request parameter has an invalid type",
                List.of(new RestFieldError(path, "Value has an invalid type")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Required request parameter is missing",
                List.of(new RestFieldError(exception.getParameterName(), "Value is required")), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported",
                List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Request media type is not supported", List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found", List.of(), request);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleDisconnectedClient() {
        // The response is no longer writable.
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unexpected REST error [traceId={}, type={}]", traceId, exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred",
                List.of(), traceId);
    }

    private ResponseEntity<ApiError> validationResponse(List<FieldError> errors, HttpServletRequest request) {
        List<RestFieldError> fields = errors.stream()
                .map(error -> new RestFieldError(error.getField(), safeValidationMessage(error.getDefaultMessage())))
                .sorted(fieldComparator())
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields, request);
    }

    private Comparator<RestFieldError> fieldComparator() {
        return Comparator.comparing(RestFieldError::path).thenComparing(RestFieldError::message);
    }

    private String safeValidationMessage(String message) {
        return message == null || message.isBlank() ? "Value is invalid" : message;
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            List<RestFieldError> fields,
            HttpServletRequest request
    ) {
        return response(status, code, message, fields, traceId(request));
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            List<RestFieldError> fields,
            String traceId
    ) {
        ApiError body = mapper.toApi(new RestError(code, message, traceId, fields));
        return ResponseEntity.status(status).contentType(PROBLEM_JSON).body(body);
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return traceId instanceof String value && !value.isBlank() ? value : UUID.randomUUID().toString();
    }
}

package org.demchenko.api.web.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.api.generated.model.ApiError;
import org.demchenko.api.web.trace.CorrelationIdFilter;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import java.util.UUID;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
@Slf4j
public class RestFallbackErrorController implements ErrorController {
    private final ErrorAttributes errorAttributes;
    private final ApiErrorMapper mapper;

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        int statusCode = statusCode(request);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String traceId = traceId(request);
        if (status.is5xxServerError()) {
            Throwable error = errorAttributes.getError(new ServletWebRequest(request));
            String type = error == null ? "Unknown" : error.getClass().getSimpleName();
            log.error("Unhandled REST error [traceId={}, type={}]", traceId, type);
        }

        String code = status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
        String message = status.is5xxServerError()
                ? "An unexpected error occurred"
                : "The request could not be processed";
        ApiError body = mapper.toApi(new RestError(code, message, traceId, List.of()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private int statusCode(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.error.status_code");
        return value instanceof Integer status ? status : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String traceId && !traceId.isBlank() ? traceId : UUID.randomUUID().toString();
    }
}

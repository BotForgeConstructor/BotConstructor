package org.demchenko.api.web.error;

import java.util.List;

public record RestError(String code, String message, String traceId, List<RestFieldError> fieldErrors) {
    public RestError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}

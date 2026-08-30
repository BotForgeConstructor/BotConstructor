package org.demchenko.api.web.error;

import org.demchenko.api.generated.model.ApiError;
import org.demchenko.api.generated.model.FieldError;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiErrorMapper {
    public ApiError toApi(RestError error) {
        List<FieldError> fields = error.fieldErrors().stream()
                .map(field -> new FieldError(field.path(), field.message()))
                .toList();
        return new ApiError(error.code(), error.message(), error.traceId(), fields);
    }
}

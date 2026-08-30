package org.demchenko.api.web;

import org.demchenko.api.generated.model.ApiError;
import org.demchenko.api.web.error.ApiErrorMapper;
import org.demchenko.api.web.error.RestError;
import org.demchenko.api.web.error.RestFieldError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorMapperTest {
    private final ApiErrorMapper mapper = new ApiErrorMapper();

    @Test
    void mapsInternalErrorToGeneratedOpenApiModel() {
        ApiError result = mapper.toApi(new RestError(
                "VALIDATION_ERROR", "Request validation failed", "trace-1",
                List.of(new RestFieldError("name", "must not be blank"))));

        assertThat(result.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(result.getMessage()).isEqualTo("Request validation failed");
        assertThat(result.getTraceId()).isEqualTo("trace-1");
        assertThat(result.getFieldErrors()).singleElement().satisfies(field -> {
            assertThat(field.getPath()).isEqualTo("name");
            assertThat(field.getMessage()).isEqualTo("must not be blank");
        });
    }

    @Test
    void mapsNullFieldErrorsToRequiredEmptyCollection() {
        ApiError result = mapper.toApi(new RestError("NOT_FOUND", "Not found", "trace-2", null));
        assertThat(result.getFieldErrors()).isEmpty();
    }
}

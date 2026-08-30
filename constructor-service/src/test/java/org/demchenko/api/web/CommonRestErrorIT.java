package org.demchenko.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.demchenko.api.web.trace.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
)
@ActiveProfiles("test")
class CommonRestErrorIT {
    private final TestRestTemplate rest;

    @Autowired
    CommonRestErrorIT(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    void validationErrorMatchesGeneratedContract() {
        HttpHeaders headers = jsonHeaders();
        headers.set(CorrelationIdFilter.HEADER_NAME, "contract-test-123");
        ResponseEntity<Map> response = rest.exchange(
                "/test/errors/validate", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", ""), headers), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertProblemContentType(response);
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isEqualTo("contract-test-123");
        assertThat(response.getBody())
                .containsEntry("code", "VALIDATION_ERROR")
                .containsEntry("message", "Request validation failed")
                .containsEntry("traceId", "contract-test-123")
                .doesNotContainKeys("stackTrace", "exception");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).hasSize(1);
        assertThat(response.getBody().toString()).doesNotContain("MethodArgumentNotValidException");
    }

    @Test
    void malformedJsonHasNoFieldErrorsOrInternalDetails() {
        ResponseEntity<Map> response = rest.exchange(
                "/test/errors/validate", HttpMethod.POST,
                new HttpEntity<>("{broken", jsonHeaders()), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("code", "MALFORMED_REQUEST");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).isEmpty();
        assertSafeTrace(response);
    }

    @Test
    void unexpectedErrorIsSanitized() {
        ResponseEntity<Map> response = rest.getForEntity("/test/errors/unexpected", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertProblemContentType(response);
        assertThat(response.getBody())
                .containsEntry("code", "INTERNAL_ERROR")
                .containsEntry("message", "An unexpected error occurred")
                .doesNotContainKeys("stackTrace", "exception");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).isEmpty();
        assertThat(response.getBody().toString())
                .doesNotContain("do-not-expose-internal-detail")
                .doesNotContain("IllegalStateException");
        assertSafeTrace(response);
    }

    @Test
    void typeMismatchIdentifiesTheParameter() {
        ResponseEntity<Map> response = rest.getForEntity("/test/errors/typed?count=not-a-number", Map.class);

        assertError(response, 400, "INVALID_PARAMETER", "Request parameter has an invalid type");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).hasSize(1);
        assertThat(response.getBody().toString()).contains("count").doesNotContain("NumberFormatException");
    }

    @Test
    void missingParameterIdentifiesTheParameter() {
        ResponseEntity<Map> response = rest.getForEntity("/test/errors/required", Map.class);

        assertError(response, 400, "MISSING_PARAMETER", "Required request parameter is missing");
        assertThat(response.getBody().toString()).contains("value");
    }

    @Test
    void unsupportedMethodUsesHttpSemantics() {
        ResponseEntity<Map> response = rest.exchange(
                "/test/errors/required?value=ok", HttpMethod.POST, HttpEntity.EMPTY, Map.class);

        assertError(response, 405, "METHOD_NOT_ALLOWED", "HTTP method is not supported");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).isEmpty();
    }

    @Test
    void unsupportedMediaTypeUsesHttpSemantics() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<Map> response = rest.exchange(
                "/test/errors/validate", HttpMethod.POST, new HttpEntity<>("name=test", headers), Map.class);

        assertError(response, 415, "UNSUPPORTED_MEDIA_TYPE", "Request media type is not supported");
    }

    @Test
    void unknownResourceUsesCanonicalNotFoundError() {
        ResponseEntity<Map> response = rest.getForEntity("/test/errors/does-not-exist", Map.class);

        assertError(response, 404, "NOT_FOUND", "Resource was not found");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void assertProblemContentType(ResponseEntity<?> response) {
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private void assertSafeTrace(ResponseEntity<Map> response) {
        String headerTrace = response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME);
        assertThat(headerTrace).isNotBlank();
        assertThat(response.getBody()).containsEntry("traceId", headerTrace);
    }

    private void assertError(ResponseEntity<Map> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertProblemContentType(response);
        assertThat(response.getBody())
                .containsEntry("code", code)
                .containsEntry("message", message)
                .doesNotContainKeys("stackTrace", "exception");
        assertThat(response.getBody().get("fieldErrors")).isNotNull();
        assertSafeTrace(response);
    }

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {
        @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("do-not-expose-internal-detail");
        }

        @GetMapping("/typed")
        void typed(@org.springframework.web.bind.annotation.RequestParam int count) {
        }

        @GetMapping("/required")
        void required(@org.springframework.web.bind.annotation.RequestParam String value) {
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }
}

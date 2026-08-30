package org.demchenko.api.web.status;

import org.demchenko.api.web.trace.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

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
class PlatformStatusIT {
    private final TestRestTemplate rest;

    @Autowired
    PlatformStatusIT(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    void realHttpEndpointMatchesGeneratedContractWithoutInternalFields() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/status", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(response.getBody())
                .containsEntry("status", "UP")
                .containsEntry("apiVersion", "v1")
                .containsOnlyKeys("status", "apiVersion", "applicationVersion");
        assertThat(response.getBody().get("applicationVersion")).isNotNull();
        assertThat(response.getBody().toString())
                .doesNotContainIgnoringCase("token", "password", "entity", "datasource", "telegram");
    }
}

package org.demchenko.api.contract;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.demchenko.api.generated.api.StatusApi;
import org.demchenko.api.generated.model.StatusResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    private static final Path SPEC = Path.of("src/main/openapi/openapi.yaml");
    private static final Set<String> REQUIRED_TAGS = Set.of(
            "Auth", "Workspace", "Bots", "Credentials", "Draft",
            "Validation", "Publish", "Versions", "Status", "Health"
    );

    @Test
    void apiV1SkeletonIsResolvableCompleteAndSecure() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(SPEC.toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).startsWith("3.1");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(api.getTags()).extracting(tag -> tag.getName()).containsAll(REQUIRED_TAGS);
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerSession");
        assertThat(api.getSecurity()).hasSize(1);

        Set<String> operationIds = new HashSet<>();
        Set<String> operationTags = new HashSet<>();
        api.getPaths().values().stream()
                .flatMap(item -> item.readOperations().stream())
                .forEach(operation -> {
                    assertThat(operation.getOperationId()).isNotBlank();
                    assertThat(operationIds.add(operation.getOperationId())).isTrue();
                    operationTags.addAll(operation.getTags());
                });
        assertThat(operationTags).containsAll(REQUIRED_TAGS);

        assertPublic(api, "/api/v1/auth/telegram/session", PathItem.HttpMethod.POST);
        assertPublic(api, "/api/v1/status", PathItem.HttpMethod.GET);
        assertPublic(api, "/api/v1/health", PathItem.HttpMethod.GET);
        assertThat(api.getPaths()).containsKeys(
                "/api/v1/workspaces", "/api/v1/workspaces/{workspaceId}/bots",
                "/api/v1/bots/{botId}/telegram-credential", "/api/v1/bots/{botId}/flows/draft",
                "/api/v1/bots/{botId}/flows/draft/validate", "/api/v1/bots/{botId}/flows/publish",
                "/api/v1/bots/{botId}/flows/versions",
                "/api/v1/bots/{botId}/flows/versions/{versionId}/activate");

        Schema<?> credential = api.getComponents().getSchemas().get("TelegramCredentialRequest");
        assertThat(credential.getProperties()).containsOnlyKeys("token");
        assertThat((Boolean) credential.getProperties().get("token").getWriteOnly()).isTrue();
        api.getComponents().getSchemas().forEach((name, schema) -> {
            if (name.endsWith("Response") || name.equals("BotResponse")) {
                assertThat(schema.getProperties()).as(name + " must not expose a token").doesNotContainKey("token");
            }
        });

        Schema<?> error = api.getComponents().getSchemas().get("ApiError");
        assertThat(error.getRequired()).containsExactlyInAnyOrder("code", "message", "traceId", "fieldErrors");
        assertThat(api.getComponents().getResponses()).allSatisfy((name, response) ->
                assertThat(response.getContent().get("application/problem+json").getSchema().get$ref())
                        .as(name).endsWith("/ApiError"));
    }

    @Test
    void generatedStatusContractIsOnTheCompilationClasspathOnly() {
        assertThat(StatusApi.PATH_GET_PLATFORM_STATUS).isEqualTo("/api/v1/status");
        assertThat(StatusResponse.class.getPackageName()).isEqualTo("org.demchenko.api.generated.model");
        assertThat(Files.exists(Path.of("src/main/java/org/demchenko/api/generated"))).isFalse();
    }

    private void assertPublic(OpenAPI api, String path, PathItem.HttpMethod method) {
        Operation operation = api.getPaths().get(path).readOperationsMap().get(method);
        assertThat(operation.getSecurity()).as(path).isEqualTo(List.of());
    }
}

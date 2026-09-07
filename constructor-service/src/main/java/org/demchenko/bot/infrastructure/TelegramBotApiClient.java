package org.demchenko.bot.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.bot.application.TelegramBotGateway;
import org.demchenko.bot.config.TelegramBotApiProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Component
public final class TelegramBotApiClient implements TelegramBotGateway {
    private final RestClient client;
    private final TelegramBotApiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramBotApiClient(RestClient.Builder builder, TelegramBotApiProperties properties) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = builder.requestFactory(requestFactory).build();
    }

    @Override
    public VerifiedBot getMe(String token) {
        JsonNode response = get(token, "getMe");
        if (!response.path("ok").asBoolean(false)) {
            throw classifyTelegramFailure(response);
        }
        JsonNode result = response.get("result");
        if (result == null || !result.path("is_bot").asBoolean(false)
                || !result.hasNonNull("id") || result.path("id").asLong(0) <= 0) {
            throw protocolError();
        }
        return new VerifiedBot(result.path("id").asLong(), result.hasNonNull("username")
                ? result.path("username").asText() : null);
    }

    @Override
    public void setWebhook(String token, String url, String secret, List<String> allowedUpdates) {
        JsonNode response = post(token, "setWebhook",
                Map.of("url", url, "secret_token", secret, "allowed_updates", allowedUpdates,
                        "drop_pending_updates", false));
        if (!response.path("ok").asBoolean(false)) {
            throw classifyTelegramFailure(response);
        }
    }

    @Override
    public void deleteWebhook(String token) {
        JsonNode response = post(token, "deleteWebhook", Map.of("drop_pending_updates", false));
        if (!response.path("ok").asBoolean(false)) {
            throw classifyTelegramFailure(response);
        }
    }

    private JsonNode get(String token, String method) {
        try {
            ResponseEntity<String> httpResponse = client.get().uri(methodUri(token, method)).retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw invalidToken();
                    })
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw rateLimited();
                    })
                    .onStatus(status -> status.is5xxServerError(), (request, response) -> {
                        throw unavailable();
                    })
                    .onStatus(status -> status.is4xxClientError(), (request, response) -> {
                        throw protocolError();
                    })
                    .toEntity(String.class);
            return parseJson(httpResponse);
        } catch (SafeApiException exception) {
            throw exception;
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new SafeApiException(503, "TELEGRAM_NETWORK_ERROR",
                    "Telegram service could not be reached");
        }
    }

    private JsonNode post(String token, String method, Object body) {
        try {
            ResponseEntity<String> httpResponse = client.post().uri(methodUri(token, method))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw invalidToken();
                    })
                    .onStatus(status -> status.value() == 429, (request, response) -> {
                        throw rateLimited();
                    })
                    .onStatus(status -> status.is5xxServerError(), (request, response) -> {
                        throw unavailable();
                    })
                    .onStatus(status -> status.is4xxClientError(), (request, response) -> {
                        throw protocolError();
                    })
                    .toEntity(String.class);
            return parseJson(httpResponse);
        } catch (SafeApiException exception) {
            throw exception;
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new SafeApiException(503, "TELEGRAM_NETWORK_ERROR",
                    "Telegram service could not be reached");
        }
    }

    private JsonNode parseJson(ResponseEntity<String> response) {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw protocolError();
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw protocolError();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw protocolError();
        }
    }

    private String methodUri(String token, String method) {
        return properties.baseUrl().replaceFirst("/+$", "") + "/bot" + token + "/" + method;
    }

    private SafeApiException classifyTelegramFailure(JsonNode response) {
        int errorCode = response.path("error_code").asInt(0);
        if (errorCode == 401 || response.path("description").asText("")
                .toLowerCase(Locale.ROOT).contains("unauthorized")) {
            return invalidToken();
        }
        if (errorCode == 429) {
            return rateLimited();
        }
        if (errorCode >= 500) {
            return unavailable();
        }
        return protocolError();
    }

    private static SafeApiException invalidToken() {
        return new SafeApiException(400, "INVALID_BOT_TOKEN", "Telegram bot token is invalid");
    }

    private static SafeApiException rateLimited() {
        return new SafeApiException(429, "TELEGRAM_RATE_LIMITED", "Telegram service is rate limited");
    }

    private static SafeApiException unavailable() {
        return new SafeApiException(503, "TELEGRAM_UNAVAILABLE", "Telegram service is unavailable");
    }

    private static SafeApiException protocolError() {
        return new SafeApiException(503, "TELEGRAM_PROTOCOL_ERROR",
                "Telegram returned an invalid response");
    }
}

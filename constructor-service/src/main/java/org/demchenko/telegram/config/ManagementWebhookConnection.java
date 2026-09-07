package org.demchenko.telegram.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.configuration.WebApplicationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.telegram.management", name = "connection-mode", havingValue = "WEBHOOK")
public class ManagementWebhookConnection {
    private final ManagementTelegramProperties management;
    private final ManagementWebhookProperties webhook;
    private final WebApplicationProperties web;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;

    public ManagementWebhookConnection(ManagementTelegramProperties management,
                                       ManagementWebhookProperties webhook,
                                       WebApplicationProperties web,
                                       ManagementTelegramApiProperties api,
                                       RestClient.Builder restClientBuilder,
                                       ObjectMapper objectMapper) {
        this.management = management;
        this.webhook = webhook;
        this.web = web;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = api.baseUrl();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(api.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(api.readTimeout());
        this.client = restClientBuilder.requestFactory(requestFactory).build();
    }

    @PostConstruct
    public void registerWebhook() {
        String url = web.url().toString().replaceFirst("/+$", "") + webhook.path();
        try {
            ResponseEntity<String> response = client.post()
                    .uri(apiUri(management.token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url, "secret_token", webhook.secret(),
                            "allowed_updates", List.of("message", "callback_query"),
                            "drop_pending_updates", false))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, ignored) -> {
                        throw registrationFailure();
                    })
                    .toEntity(String.class);
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                throw registrationFailure();
            }
            JsonNode body = objectMapper.readTree(response.getBody());
            if (body == null || !body.path("ok").asBoolean(false)) {
                throw registrationFailure();
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw registrationFailure();
        } catch (Exception exception) {
            throw registrationFailure();
        }
        log.info("Management Telegram webhook registered");
    }

    private String apiUri(String token) {
        return apiBaseUrl.replaceFirst("/+$", "") + "/bot" + token + "/setWebhook";
    }

    private static IllegalStateException registrationFailure() {
        return new IllegalStateException("management webhook registration failed");
    }
}

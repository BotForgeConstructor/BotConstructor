package org.demchenko.telegram.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.telegram.config.ManagementWebhookProperties;
import org.demchenko.telegram.dispetcher.BotUpdateDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.telegram.management", name = "connection-mode", havingValue = "WEBHOOK")
@ConditionalOnProperty(prefix = "app.telegram.management.webhook", name = "enabled", havingValue = "true")
public class ManagementWebhookController {
    private final ManagementWebhookProperties properties;
    private final BotUpdateDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @PostMapping("${app.telegram.management.webhook.path:/api/v1/telegram/management/webhook}")
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String header,
                                        @RequestBody JsonNode body) {
        if (header == null || properties.secret() == null || !MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8), properties.secret().getBytes(StandardCharsets.UTF_8))) {
            throw new SafeApiException(401, "AUTHENTICATION_REQUIRED", "Authentication is required");
        }
        try {
            dispatcher.dispatch(objectMapper.treeToValue(body, Update.class));
            return ResponseEntity.ok().build();
        } catch (SafeApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SafeApiException(400, "MALFORMED_REQUEST", "Request body is malformed");
        }
    }
}

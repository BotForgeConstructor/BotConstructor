package org.demchenko.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.demchenko.telegram.config.ManagementTelegramApiProperties;
import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.demchenko.telegram.config.ManagementWebhookConnection;
import org.demchenko.telegram.config.ManagementWebhookProperties;
import org.demchenko.telegram.config.TelegramBotConnection;
import org.demchenko.telegram.dispetcher.BotUpdateDispatcher;
import org.demchenko.telegram.web.ManagementWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManagementRuntimeModeTest {
    @Test
    void productionProfilePinsWebhookModeAndCannotEnableLongPolling() throws Exception {
        String production = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(production).contains("connection-mode: WEBHOOK")
                .doesNotContain("APP_TELEGRAM_MANAGEMENT_CONNECTION_MODE");
    }

    @Test
    void webhookModeCreatesWebhookAdaptersAndNeverCreatesPolling() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(RuntimeConfiguration.class)
                    .withBean(BotUpdateDispatcher.class, () -> mock(BotUpdateDispatcher.class))
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withBean(RestClient.Builder.class, RestClient::builder)
                    .withPropertyValues(
                            "app.telegram.management.enabled=true",
                            "app.telegram.management.connection-mode=WEBHOOK",
                            "app.telegram.management.token=555555555:test_runtime_mode_token_0123456789",
                            "app.telegram.management.username=runtime_test_bot",
                            "app.telegram.management.webhook.enabled=true",
                            "app.telegram.management.webhook.secret=runtime_webhook_secret",
                            "app.telegram.management.webhook.path=/api/v1/telegram/management/webhook",
                            "app.telegram.api.base-url=http://localhost:" + server.getAddress().getPort(),
                            "app.telegram.api.connect-timeout=1s",
                            "app.telegram.api.read-timeout=1s",
                            "app.web.url=https://backend.invalid")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(ManagementWebhookConnection.class);
                        assertThat(context).hasSingleBean(ManagementWebhookController.class);
                        assertThat(context).doesNotHaveBean(TelegramBotConnection.class);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ManagementTelegramProperties.class,
            ManagementWebhookProperties.class,
            ManagementTelegramApiProperties.class,
            WebApplicationProperties.class
    })
    @Import({ManagementWebhookConnection.class, ManagementWebhookController.class, TelegramBotConnection.class})
    static class RuntimeConfiguration {
    }
}

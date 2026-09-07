package org.demchenko.bot;

import com.sun.net.httpserver.HttpServer;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.bot.config.TelegramBotApiProperties;
import org.demchenko.bot.infrastructure.TelegramBotApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramBotApiClientTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void classifiesTelegramHttpFailuresWithoutLeakingToken() throws Exception {
        assertStatus(401, "INVALID_BOT_TOKEN");
        assertStatus(429, "TELEGRAM_RATE_LIMITED");
        assertStatus(500, "TELEGRAM_UNAVAILABLE");
    }

    @Test
    void acceptsVerifiedGetMeResponse() throws Exception {
        start(200, "{\"ok\":true,\"result\":{\"id\":12345,\"is_bot\":true,\"username\":\"safe_bot\"}}");
        var client = client();
        var result = client.getMe("123456:token-marker-that-must-not-appear");
        assertThat(result.id()).isEqualTo(12345L);
        assertThat(result.username()).isEqualTo("safe_bot");
    }

    @Test
    void classifiesMalformedTelegramResponseAsProtocolError() throws Exception {
        start(200, "{\"ok\":true,\"result\":{}}");
        assertThatThrownBy(() -> client().getMe("token-marker"))
                .isInstanceOf(SafeApiException.class)
                .satisfies(error -> {
                    var safe = (SafeApiException) error;
                    assertThat(safe.code()).isEqualTo("TELEGRAM_PROTOCOL_ERROR");
                    assertThat(safe.getMessage()).doesNotContain("token-marker");
                });
    }

    @Test
    void rejectsEmptyUnexpectedContentTypeOkFalseAndMissingIdentityAsProtocolErrors() throws Exception {
        assertProtocolResponse(200, "", "application/json");
        assertProtocolResponse(200, "{\"ok\":true,\"result\":{\"id\":1,\"is_bot\":true}}", "text/plain");
        assertProtocolResponse(200, "{\"ok\":false,\"error_code\":400}", "application/json");
        assertProtocolResponse(200, "{\"ok\":true}", "application/json");
        assertProtocolResponse(200, "{\"ok\":true,\"result\":{\"is_bot\":true}}", "application/json");
    }

    @Test
    void classifiesReadTimeoutAndConnectionRefusedAsNetworkErrors() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        TelegramBotApiClient timeoutClient = new TelegramBotApiClient(RestClient.builder(),
                new TelegramBotApiProperties("http://localhost:" + server.getAddress().getPort(),
                        Duration.ofMillis(200), Duration.ofMillis(100)));
        assertSafeCode(() -> timeoutClient.getMe("timeout-marker"), "TELEGRAM_NETWORK_ERROR");
        release.countDown();
        server.stop(0);
        server = null;

        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        TelegramBotApiClient refused = new TelegramBotApiClient(RestClient.builder(),
                new TelegramBotApiProperties("http://localhost:" + unusedPort,
                        Duration.ofMillis(200), Duration.ofMillis(200)));
        assertSafeCode(() -> refused.getMe("refused-marker"), "TELEGRAM_NETWORK_ERROR");
    }

    @Test
    void setWebhookUsesSameSafeFailureClassification() throws Exception {
        assertWebhookStatus(401, "INVALID_BOT_TOKEN");
        assertWebhookStatus(429, "TELEGRAM_RATE_LIMITED");
        assertWebhookStatus(500, "TELEGRAM_UNAVAILABLE");
        start(200, "not-json");
        assertSafeCode(() -> client().setWebhook("webhook-marker", "https://example.invalid/hook",
                "safe_secret", List.of("message")), "TELEGRAM_PROTOCOL_ERROR");
    }

    @Test
    void setWebhookUsesExpectedMethodAndSafePayload() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"ok\":true,\"result\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();

        String token = "123456:test-token-marker";
        String secret = "test_webhook_secret_marker";
        client().setWebhook(token, "https://public.invalid/api/v1/telegram/webhooks/bot-id", secret,
                List.of("message", "callback_query"));

        assertThat(path.get()).isEqualTo("/bot" + token + "/setWebhook");
        assertThat(requestBody.get()).contains("https://public.invalid/api/v1/telegram/webhooks/bot-id")
                .contains("\"secret_token\":\"" + secret + "\"")
                .contains("message", "callback_query", "\"drop_pending_updates\":false");
        assertThat(requestBody.get()).doesNotContain(token);
    }

    private void assertStatus(int status, String code) throws Exception {
        start(status, "{\"ok\":false,\"error_code\":" + status + "}");
        assertThatThrownBy(() -> client().getMe("token-marker"))
                .isInstanceOf(SafeApiException.class)
                .satisfies(error -> assertThat(((SafeApiException) error).code()).isEqualTo(code));
        server.stop(0);
        server = null;
    }

    private void assertWebhookStatus(int status, String code) throws Exception {
        start(status, "{\"ok\":false,\"error_code\":" + status + "}");
        assertSafeCode(() -> client().setWebhook("webhook-marker", "https://example.invalid/hook",
                "safe_secret", List.of("message")), code);
        server.stop(0);
        server = null;
    }

    private void assertProtocolResponse(int status, String body, String contentType) throws Exception {
        start(status, body, contentType);
        assertSafeCode(() -> client().getMe("protocol-marker"), "TELEGRAM_PROTOCOL_ERROR");
        server.stop(0);
        server = null;
    }

    private void assertSafeCode(Runnable call, String code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(SafeApiException.class)
                .satisfies(error -> {
                    SafeApiException safe = (SafeApiException) error;
                    assertThat(safe.code()).isEqualTo(code);
                    assertThat(safe.getMessage().contains("marker")).isFalse();
                });
    }

    private TelegramBotApiClient client() {
        return new TelegramBotApiClient(RestClient.builder(),
                new TelegramBotApiProperties("http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    private void start(int status, String body) throws Exception {
        start(status, body, "application/json");
    }

    private void start(int status, String body, String contentType) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }
}

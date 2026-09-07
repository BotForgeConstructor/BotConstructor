package org.demchenko.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.demchenko.configuration.WebApplicationProperties;
import org.demchenko.telegram.config.ManagementTelegramApiProperties;
import org.demchenko.telegram.config.ManagementTelegramProperties;
import org.demchenko.telegram.config.ManagementWebhookConnection;
import org.demchenko.telegram.config.ManagementWebhookProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementWebhookConnectionTest {
    private static final String TOKEN = "444444444:test_management_transport_marker_012345";
    private static final String SECRET = "management_webhook_secret_marker";
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registersExpectedWebhookUsingBoundedLocalTransport() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<JsonNode> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new ObjectMapper().readTree(exchange.getRequestBody()));
            respond(exchange, 200, "application/json", "{\"ok\":true,\"result\":true}");
        });
        server.start();

        connection(baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1)).registerWebhook();

        assertThat(path.get()).isEqualTo("/bot" + TOKEN + "/setWebhook");
        assertThat(body.get().path("url").asText()).isEqualTo(
                "https://backend.invalid/api/v1/telegram/management/webhook");
        assertThat(body.get().path("secret_token").asText()).isEqualTo(SECRET);
        assertThat(body.get().path("drop_pending_updates").asBoolean()).isFalse();
        assertThat(body.get().path("allowed_updates").toString()).contains("message", "callback_query");
        assertThat(body.get().path("url").asText()).doesNotContain(TOKEN, SECRET);
    }

    @Test
    void mapsHttpMalformedAndUnexpectedContentFailuresToOneRedactedStartupError() throws Exception {
        assertSafeFailure(401, "application/json", "{\"ok\":false}");
        assertSafeFailure(429, "application/json", "{\"ok\":false}");
        assertSafeFailure(500, "application/json", "{\"ok\":false}");
        assertSafeFailure(200, "application/json", "not-json");
        assertSafeFailure(200, "text/plain", "{\"ok\":true}");
    }

    @Test
    void mapsReadTimeoutAndConnectionRefusedWithoutCredentialDisclosure() throws Exception {
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
        assertRedactedFailure(connection(baseUrl(), Duration.ofMillis(200), Duration.ofMillis(100))::registerWebhook);
        release.countDown();
        server.stop(0);
        server = null;

        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        ManagementWebhookConnection refused = connection("http://localhost:" + unusedPort,
                Duration.ofMillis(200), Duration.ofMillis(200));
        assertRedactedFailure(refused::registerWebhook);
    }

    private void assertSafeFailure(int status, String contentType, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> respond(exchange, status, contentType, body));
        server.start();
        assertRedactedFailure(connection(baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1))::registerWebhook);
        server.stop(0);
        server = null;
    }

    private void assertRedactedFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("management webhook registration failed")
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(TOKEN, SECRET));
    }

    private ManagementWebhookConnection connection(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return new ManagementWebhookConnection(
                new ManagementTelegramProperties(true, ManagementTelegramProperties.ConnectionMode.WEBHOOK,
                        TOKEN, "management_test_bot"),
                new ManagementWebhookProperties(true, SECRET,
                        URI.create("/api/v1/telegram/management/webhook")),
                new WebApplicationProperties(URI.create("https://backend.invalid")),
                new ManagementTelegramApiProperties(baseUrl, connectTimeout, readTimeout),
                RestClient.builder(), new ObjectMapper());
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                String contentType, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

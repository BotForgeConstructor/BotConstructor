package org.demchenko.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.demchenko.ConstructorApplication;
import org.demchenko.auth.TestInitDataSigner;
import org.demchenko.bot.application.BotApplicationService;
import org.demchenko.bot.application.BotCredentialCipher;
import org.demchenko.bot.application.BotCredentialPersistenceService;
import org.demchenko.bot.application.TelegramBotGateway;
import org.demchenko.bot.config.CredentialEncryptionProperties;
import org.demchenko.bot.infrastructure.TelegramBotApiClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(BotOnboardingIT.ProbeConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BotOnboardingIT {
    private static final String MANAGEMENT_TOKEN = "123456789:test_only_management_bot_token_value";
    private static final String TOKEN_A = "111111111:phase2_test_token_a_marker_0123456789";
    private static final String TOKEN_B_WEBHOOK_FAILURE = "222222222:phase2_webhook_failure_marker_012345";
    private static final String TOKEN_C = "333333333:phase2_test_token_c_marker_0123456789";
    private static final String TOKEN_D = "444444444:phase2_concurrent_winner_marker_012345";
    private static final String TOKEN_E = "555555555:phase2_concurrent_loser_marker_0123456";
    private static final String TOKEN_INVALID = "666666666:phase2_invalid_token_marker_012345678";
    private static final String TOKEN_RATE_LIMITED = "777777777:phase2_rate_limit_marker_0123456789";
    private static final String TOKEN_MALFORMED = "888888888:phase2_protocol_marker_012345678901";
    private static final TestTelegramServer TELEGRAM = TestTelegramServer.start();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-bookworm")
            .withDatabaseName("bot_onboarding_test")
            .withUsername("bot_onboarding_test")
            .withPassword("obvious_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.telegram.api.base-url", TELEGRAM::baseUrl);
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BotCredentialCipher cipher;
    @Autowired
    CredentialEncryptionProperties encryption;
    @Autowired
    BotCredentialPersistenceService credentialPersistence;
    @Autowired
    TransactionProbeGateway probe;

    @BeforeEach
    void clean() {
        jdbc.update("delete from bot_credential_history");
        jdbc.update("delete from bot_credential_operations");
        jdbc.update("delete from bot_credentials");
        jdbc.update("delete from bot_idempotency_records");
        jdbc.update("delete from bots");
        jdbc.update("delete from memberships");
        jdbc.update("delete from workspaces");
        jdbc.update("delete from platform_users");
        TELEGRAM.reset();
        probe.reset();
    }

    @AfterAll
    static void stopTelegram() {
        TELEGRAM.close();
    }

    @Test
    void completePhaseTwoOnboardingReplacementReconnectRecoveryAndIsolation(CapturedOutput output) {
        Session ownerA = authenticate(810_001L, "OwnerA");
        Session ownerB = authenticate(810_002L, "OwnerB");
        UUID botId = createBot(ownerA, "phase2-e2e-key", "Phase 2 Bot");

        ResponseEntity<Map> first = credential(ownerA, botId, TOKEN_A);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertSafeCredentialResponse(first);
        assertThat(first.getBody()).containsEntry("telegramBotId", 910001);
        assertEncryptedActive(botId, TOKEN_A);
        assertWebhookIsSafe(botId, TOKEN_A);

        ResponseEntity<Map> failedReplacement = credential(ownerA, botId, TOKEN_B_WEBHOOK_FAILURE);
        assertThat(failedReplacement.getStatusCode().value()).isEqualTo(503);
        assertCommonError(failedReplacement, "TELEGRAM_UNAVAILABLE");
        assertEncryptedActive(botId, TOKEN_A);
        assertThat(jdbc.queryForObject("select count(*) from bot_credential_operations where bot_id=? and state='FAILED'",
                Integer.class, botId)).isEqualTo(1);

        ResponseEntity<Map> replacement = credential(ownerA, botId, TOKEN_C);
        assertThat(replacement.getStatusCode().value()).isEqualTo(200);
        assertEncryptedActive(botId, TOKEN_C);
        assertThat(jdbc.queryForObject("select count(*) from bot_credential_history where bot_id=? and state='REPLACED'",
                Integer.class, botId)).isEqualTo(1);
        assertEncryptedHistory(botId, TOKEN_A);

        concurrentReplacementHasOneWinner(ownerA, botId);
        assertEncryptedActive(botId, TOKEN_D);
        failedGetMePreservesActive(ownerA, botId, TOKEN_INVALID, 400, "INVALID_BOT_TOKEN");
        failedGetMePreservesActive(ownerA, botId, TOKEN_RATE_LIMITED, 429, "TELEGRAM_RATE_LIMITED");
        failedGetMePreservesActive(ownerA, botId, TOKEN_MALFORMED, 503, "TELEGRAM_PROTOCOL_ERROR");

        int credentialRows = jdbc.queryForObject("select count(*) from bot_credentials where bot_id=?",
                Integer.class, botId);
        concurrentReconnectIsIdempotent(ownerA, botId);
        reconnect(ownerA, botId, 200);
        assertThat(jdbc.queryForObject("select count(*) from bot_credentials where bot_id=?",
                Integer.class, botId)).isEqualTo(credentialRows);

        int callsBeforeDeny = probe.totalCalls();
        assertCommonError(credential(ownerB, botId, TOKEN_A), "NOT_FOUND");
        assertCommonError(reconnect(ownerB, botId), "NOT_FOUND");
        assertThat(probe.totalCalls()).isEqualTo(callsBeforeDeny);

        credentialPersistence.prepareReconnect(ownerA.userId(), botId);
        assertThat(jdbc.queryForObject("""
                select count(*) from bot_credential_operations
                where bot_id=? and operation_type='RECONNECT' and state='PENDING_VERIFICATION'
                """, Integer.class, botId)).isEqualTo(1);
        restartAndReconnect(ownerA, botId);
        assertThat(jdbc.queryForObject("select count(*) from bot_credentials where bot_id=? and status='ACTIVE'",
                Integer.class, botId)).isEqualTo(1);
        assertThat(probe.transactionStates()).isNotEmpty().allMatch(active -> !active);
        assertNoSensitiveOutput(output, TOKEN_A, TOKEN_B_WEBHOOK_FAILURE, TOKEN_C, TOKEN_D, TOKEN_E,
                TOKEN_INVALID, TOKEN_RATE_LIMITED, TOKEN_MALFORMED, encryption.key());
    }

    private Session authenticate(long telegramId, String firstName) {
        String user = "{\"id\":" + telegramId + ",\"first_name\":\"" + firstName + "\"}";
        String initData = TestInitDataSigner.sign(MANAGEMENT_TOKEN, Instant.now().getEpochSecond(), user);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = rest.exchange("/api/v1/auth/telegram/session", HttpMethod.POST,
                new HttpEntity<>(Map.of("initData", initData), headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map workspace = (Map) response.getBody().get("defaultWorkspace");
        Map userBody = (Map) response.getBody().get("user");
        return new Session(response.getBody().get("accessToken").toString(),
                UUID.fromString(workspace.get("id").toString()),
                UUID.fromString(userBody.get("id").toString()));
    }

    private UUID createBot(Session session, String key, String name) {
        HttpHeaders headers = bearer(session.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        ResponseEntity<Map> response = rest.exchange("/api/v1/workspaces/" + session.workspaceId() + "/bots",
                HttpMethod.POST, new HttpEntity<>(Map.of("displayName", name), headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return UUID.fromString(response.getBody().get("id").toString());
    }

    private ResponseEntity<Map> credential(Session session, UUID botId, String token) {
        HttpHeaders headers = bearer(session.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/bots/" + botId + "/telegram-credential", HttpMethod.PUT,
                new HttpEntity<>(Map.of("token", token), headers), Map.class);
    }

    private ResponseEntity<Map> reconnect(Session session, UUID botId) {
        return rest.exchange("/api/v1/bots/" + botId + "/telegram-credential/reconnect", HttpMethod.POST,
                new HttpEntity<>(bearer(session.token())), Map.class);
    }

    private void reconnect(Session session, UUID botId, int expectedStatus) {
        assertThat(reconnect(session, botId).getStatusCode().value()).isEqualTo(expectedStatus);
    }

    private void concurrentReconnectIsIdempotent(Session session, UUID botId) {
        int operationsBefore = jdbc.queryForObject(
                "select count(*) from bot_credential_operations where bot_id=?", Integer.class, botId);
        probe.blockNextWebhookCalls(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ResponseEntity<Map>> first = executor.submit(() -> reconnect(session, botId));
            Future<ResponseEntity<Map>> second = executor.submit(() -> reconnect(session, botId));
            assertThat(first.get(20, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent reconnect did not complete safely", exception);
        } finally {
            probe.releaseWebhookCalls();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from bot_credential_operations where bot_id=?", Integer.class, botId))
                .isEqualTo(operationsBefore + 1);
        assertThat(jdbc.queryForObject(
                "select count(*) from bot_credentials where bot_id=? and status='ACTIVE'", Integer.class, botId))
                .isEqualTo(1);
    }

    private void concurrentReplacementHasOneWinner(Session session, UUID botId) {
        probe.blockNextGetMeCall();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ResponseEntity<Map>> winner = executor.submit(() -> credential(session, botId, TOKEN_D));
            probe.awaitBlockedGetMe();
            ResponseEntity<Map> conflict = credential(session, botId, TOKEN_E);
            assertThat(conflict.getStatusCode().value()).isEqualTo(409);
            assertCommonError(conflict, "ONBOARDING_IN_PROGRESS");
            probe.releaseGetMeCall();
            assertThat(winner.get(20, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent replacement did not complete safely", exception);
        } finally {
            probe.releaseGetMeCall();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from bot_credentials where bot_id=? and status='ACTIVE'", Integer.class, botId))
                .isEqualTo(1);
    }

    private void failedGetMePreservesActive(Session session, UUID botId, String rejectedToken,
                                            int expectedStatus, String expectedCode) {
        int webhooksBefore = TELEGRAM.webhooks().size();
        ResponseEntity<Map> response = credential(session, botId, rejectedToken);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertCommonError(response, expectedCode);
        assertThat(response.getBody().toString()).doesNotContain(rejectedToken);
        assertEncryptedActive(botId, TOKEN_D);
        assertThat(storedTextForBot(botId)).doesNotContain(rejectedToken, encryption.key());
        assertThat(TELEGRAM.webhooks()).hasSize(webhooksBefore);
    }

    private void assertEncryptedActive(UUID botId, String expectedToken) {
        Map<String, Object> row = jdbc.queryForMap("""
                select encrypted_token, encrypted_webhook_secret, token_key_id, webhook_key_id, status
                from bot_credentials where bot_id=?
                """, botId);
        String encryptedToken = row.get("encrypted_token").toString();
        String encryptedSecret = row.get("encrypted_webhook_secret").toString();
        assertThat(encryptedToken.contains(expectedToken)).isFalse();
        assertThat(encryptedSecret.contains(expectedToken)).isFalse();
        assertThat(row.get("token_key_id")).isEqualTo(encryption.keyId()).isNotEqualTo("active");
        assertThat(row.get("webhook_key_id")).isEqualTo(encryption.keyId()).isNotEqualTo("active");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertSecretEquals(expectedToken, cipher.decrypt(encryptedToken, botId + ":telegram-token"));
        String webhookSecret = cipher.decrypt(encryptedSecret, botId + ":webhook-secret");
        assertThat(webhookSecret).matches("[A-Za-z0-9_-]{43}");
        assertThat(encryptedSecret.contains(webhookSecret)).isFalse();
        assertThat(storedTextForBot(botId).contains(encryption.key())).isFalse();
    }

    private void assertEncryptedHistory(UUID botId, String expectedToken) {
        String envelope = jdbc.queryForObject("""
                select encrypted_token from bot_credential_history
                where bot_id=? order by replaced_at desc limit 1
                """, String.class, botId);
        assertThat(envelope.contains(expectedToken)).isFalse();
        assertSecretEquals(expectedToken, cipher.decrypt(envelope, botId + ":telegram-token"));
    }

    private String storedTextForBot(UUID botId) {
        return String.join("|", jdbc.queryForList("""
                select encrypted_token || encrypted_webhook_secret || token_key_id || webhook_key_id
                from bot_credentials where bot_id=?
                union all
                select encrypted_token || encrypted_webhook_secret || token_key_id || webhook_key_id
                from bot_credential_operations where bot_id=?
                union all
                select encrypted_token || encrypted_webhook_secret || token_key_id || webhook_key_id
                from bot_credential_history where bot_id=?
                """, String.class, botId, botId, botId));
    }

    private void assertWebhookIsSafe(UUID botId, String token) {
        JsonNode request = TELEGRAM.lastWebhook();
        assertThat(request.path("url").asText())
                .isEqualTo("https://test.invalid/api/v1/telegram/webhooks/" + botId);
        assertThat(request.path("url").asText().contains(token)).isFalse();
        assertThat(request.path("url").asText().contains(request.path("secret_token").asText())).isFalse();
        assertThat(request.path("allowed_updates").toString()).contains("message", "callback_query");
        assertThat(request.path("drop_pending_updates").asBoolean()).isFalse();
    }

    private void assertSafeCredentialResponse(ResponseEntity<Map> response) {
        String body = response.getBody().toString();
        assertThat(body.contains("token")).isFalse();
        assertThat(body.contains("ciphertext")).isFalse();
        assertThat(body.contains("webhookSecret")).isFalse();
        assertThat(body.contains("keyId")).isFalse();
    }

    private void assertCommonError(ResponseEntity<Map> response, String code) {
        assertThat(response.getBody()).containsEntry("code", code).containsKey("traceId");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).isEmpty();
        String body = response.getBody().toString();
        assertThat(body.contains("stackTrace") || body.contains("ciphertext") || body.contains("webhookSecret"))
                .isFalse();
    }

    private void restartAndReconnect(Session owner, UUID botId) {
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(
                ConstructorApplication.class, ProbeConfiguration.class)
                .profiles("test")
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run(
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--app.telegram.api.base-url=" + TELEGRAM.baseUrl())) {
            restarted.getBean(BotApplicationService.class).reconnect(owner.userId(), botId,
                    "https://test.invalid/api/v1/telegram/webhooks/" + botId);
            assertThat(restarted.getBean(TransactionProbeGateway.class).transactionStates())
                    .isNotEmpty().allMatch(active -> !active);
        }
    }

    private void assertNoSensitiveOutput(CapturedOutput output, String... values) {
        String logs = output.getAll();
        for (String value : values) {
            assertThat(logs.contains(value)).as("captured logs contain no credential material").isFalse();
        }
        for (JsonNode request : TELEGRAM.webhooks()) {
            String secret = request.path("secret_token").asText();
            assertThat(logs.contains(secret)).as("captured logs contain no webhook secret").isFalse();
        }
    }

    private static void assertSecretEquals(String expected, String actual) {
        assertThat(MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    record Session(String token, UUID workspaceId, UUID userId) {
        @Override
        public String toString() {
            return "Session[workspaceId=" + workspaceId + ", userId=" + userId + "]";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        @Primary
        TransactionProbeGateway transactionProbeGateway(TelegramBotApiClient delegate) {
            return new TransactionProbeGateway(delegate);
        }
    }

    static final class TransactionProbeGateway implements TelegramBotGateway {
        private final TelegramBotApiClient delegate;
        private final CopyOnWriteArrayList<Boolean> transactionStates = new CopyOnWriteArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile CyclicBarrier webhookBarrier;
        private volatile CountDownLatch getMeEntered;
        private volatile CountDownLatch getMeRelease;

        TransactionProbeGateway(TelegramBotApiClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public VerifiedBot getMe(String token) {
            recordTransaction();
            CountDownLatch entered = getMeEntered;
            CountDownLatch release = getMeRelease;
            if (entered != null && release != null) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release getMe call");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("getMe concurrency barrier interrupted", exception);
                } finally {
                    getMeEntered = null;
                    getMeRelease = null;
                }
            }
            return delegate.getMe(token);
        }

        @Override
        public void setWebhook(String token, String url, String secret, List<String> allowedUpdates) {
            recordTransaction();
            CyclicBarrier barrier = webhookBarrier;
            if (barrier != null) {
                try {
                    barrier.await();
                } catch (Exception exception) {
                    throw new AssertionError("Webhook concurrency barrier failed", exception);
                }
            }
            delegate.setWebhook(token, url, secret, allowedUpdates);
        }

        @Override
        public void deleteWebhook(String token) {
            recordTransaction();
            delegate.deleteWebhook(token);
        }

        void reset() {
            transactionStates.clear();
            calls.set(0);
            webhookBarrier = null;
            getMeEntered = null;
            getMeRelease = null;
        }

        int totalCalls() {
            return calls.get();
        }

        List<Boolean> transactionStates() {
            return List.copyOf(transactionStates);
        }

        void blockNextWebhookCalls(int parties) {
            webhookBarrier = new CyclicBarrier(parties, () -> webhookBarrier = null);
        }

        void releaseWebhookCalls() {
            webhookBarrier = null;
        }

        void blockNextGetMeCall() {
            getMeEntered = new CountDownLatch(1);
            getMeRelease = new CountDownLatch(1);
        }

        void awaitBlockedGetMe() {
            try {
                assertThat(getMeEntered.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for getMe call", exception);
            }
        }

        void releaseGetMeCall() {
            CountDownLatch release = getMeRelease;
            if (release != null) {
                release.countDown();
            }
        }

        private void recordTransaction() {
            calls.incrementAndGet();
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
        }
    }

    static final class TestTelegramServer implements AutoCloseable {
        private final HttpServer server;
        private final ObjectMapper mapper = new ObjectMapper();
        private final CopyOnWriteArrayList<JsonNode> webhooks = new CopyOnWriteArrayList<>();

        private TestTelegramServer(HttpServer server) {
            this.server = server;
        }

        static TestTelegramServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                TestTelegramServer result = new TestTelegramServer(server);
                server.createContext("/", result::handle);
                server.start();
                return result;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start local Telegram stub", exception);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void reset() {
            webhooks.clear();
        }

        JsonNode lastWebhook() {
            return webhooks.getLast();
        }

        List<JsonNode> webhooks() {
            return new ArrayList<>(webhooks);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String token = path.substring("/bot".length(), path.lastIndexOf('/'));
            String method = path.substring(path.lastIndexOf('/') + 1);
            if (method.equals("getMe")) {
                if (TOKEN_INVALID.equals(token)) {
                    send(exchange, 401, "{\"ok\":false,\"error_code\":401}");
                    return;
                }
                if (TOKEN_RATE_LIMITED.equals(token)) {
                    send(exchange, 429, "{\"ok\":false,\"error_code\":429}");
                    return;
                }
                if (TOKEN_MALFORMED.equals(token)) {
                    send(exchange, 200, "not-json");
                    return;
                }
                send(exchange, 200, getMe(token));
                return;
            }
            if (method.equals("setWebhook")) {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                webhooks.add(request);
                if (TOKEN_B_WEBHOOK_FAILURE.equals(token)) {
                    send(exchange, 500, "{\"ok\":false,\"error_code\":500}");
                } else {
                    send(exchange, 200, "{\"ok\":true,\"result\":true}");
                }
                return;
            }
            if (method.equals("deleteWebhook")) {
                send(exchange, 200, "{\"ok\":true,\"result\":true}");
                return;
            }
            send(exchange, 404, "{\"ok\":false,\"error_code\":404}");
        }

        private String getMe(String token) {
            long id = TOKEN_A.equals(token) ? 910001L
                    : TOKEN_B_WEBHOOK_FAILURE.equals(token) ? 910002L
                    : TOKEN_C.equals(token) ? 910003L
                    : TOKEN_D.equals(token) ? 910004L : 910005L;
            return "{\"ok\":true,\"result\":{\"id\":" + id
                    + ",\"is_bot\":true,\"username\":\"phase2_test_bot\"}}";
        }

        private void send(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}

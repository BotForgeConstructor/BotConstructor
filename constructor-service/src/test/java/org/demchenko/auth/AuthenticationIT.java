package org.demchenko.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.demchenko.auth.application.AuthenticatedTelegramUser;
import org.demchenko.auth.application.IdentityWorkspaceBootstrapService;
import org.demchenko.bot.data.BotRepository;
import org.demchenko.bot.data.BotIdempotencyRepository;
import org.demchenko.bot.model.BotEntity;
import org.demchenko.identity.data.repo.PlatformUserRepository;
import org.demchenko.workspace.data.MembershipRepository;
import org.demchenko.workspace.data.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import org.demchenko.workspace.model.MembershipEntity;
import org.demchenko.workspace.model.MembershipRole;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthenticationIT {
    private static final String TOKEN = "123456789:test_only_management_bot_token_value";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-bookworm")
            .withDatabaseName("auth_test").withUsername("auth_test").withPassword("obvious_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired PlatformUserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired MembershipRepository memberships;
    @Autowired BotRepository bots;
    @Autowired BotIdempotencyRepository idempotencies;
    @Autowired IdentityWorkspaceBootstrapService bootstrap;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        bots.deleteAll();
        memberships.deleteAll();
        workspaces.deleteAll();
        users.deleteAll();
    }

    @Test
    void validAuthenticationBootstrapsExactlyOnceAndReturnsShortSession() {
        ResponseEntity<Map> first = authenticate(700_001L, "First");
        ResponseEntity<Map> second = authenticate(700_001L, "Renamed");

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(first.getBody()).containsEntry("tokenType", "Bearer").containsKey("accessToken")
                .containsEntry("expiresIn", 900).containsKey("expiresAt");
        assertThat(first.getBody().toString()).doesNotContain("initData").doesNotContain(TOKEN);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(users.count()).isEqualTo(1);
        assertThat(workspaces.count()).isEqualTo(1);
        assertThat(memberships.count()).isEqualTo(1);
        assertThat(users.findByTelegramUserId(700_001L).orElseThrow().getFirstName()).isEqualTo("Renamed");
        assertThat(workspaces.findAll().getFirst().isDefaultWorkspace()).isTrue();
        assertThat(memberships.findAll().getFirst().getRole().name()).isEqualTo("OWNER");
    }

    @Test
    void invalidExpiredAndMalformedInitDataUseSafeErrorContract() {
        assertAuthError("auth_date=1&user=x&hash=" + "00".repeat(32), "INVALID_TELEGRAM_INIT_DATA");
        assertAuthError(TestInitDataSigner.sign(TOKEN, Instant.now().minusSeconds(301).getEpochSecond(),
                "{\"id\":1,\"first_name\":\"Expired\"}"), "EXPIRED_TELEGRAM_INIT_DATA");
        assertAuthError("not-a-query", "MALFORMED_TELEGRAM_INIT_DATA");
    }

    @Test
    void protectedWorkspaceRejectsMissingMalformedAndCrossTenantSessions() {
        Map authA = authenticate(710_001L, "A").getBody();
        UUID workspaceA = UUID.fromString(((Map) authA.get("defaultWorkspace")).get("id").toString());
        Map authB = authenticate(710_002L, "B").getBody();
        UUID workspaceB = UUID.fromString(((Map) authB.get("defaultWorkspace")).get("id").toString());
        String tokenA = authA.get("accessToken").toString();

        assertSecurityError(getWorkspace(workspaceA, null), 401, "INVALID_SESSION");
        assertSecurityError(getWorkspace(workspaceA, "not-a-jwt"), 401, "INVALID_SESSION");
        assertSecurityError(getWorkspace(workspaceA, tokenA.substring(0, tokenA.length() - 2) + "xx"), 401, "INVALID_SESSION");
        assertSecurityError(getWorkspace(workspaceA, expiredToken(UUID.fromString(((Map) authA.get("user")).get("id").toString()))),
                401, "INVALID_SESSION");
        assertThat(getWorkspace(workspaceA, tokenA).getStatusCode().value()).isEqualTo(200);
        assertSecurityError(getWorkspace(workspaceB, tokenA), 404, "NOT_FOUND");

        BotEntity botA = bot(workspaceA, "A bot");
        BotEntity botB = bot(workspaceB, "B bot");
        bots.saveAllAndFlush(List.of(botA, botB));
        assertThat(bots.findByIdAndWorkspaceId(botA.getId(), workspaceA)).isPresent();
        assertThat(bots.findByIdAndWorkspaceId(botB.getId(), workspaceA)).isEmpty();
    }

    @Test
    void concurrentFirstLoginCreatesOneUserDefaultWorkspaceAndMembership() throws Exception {
        var user = new AuthenticatedTelegramUser(720_001L, "parallel", "Parallel", null, "en");
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<java.util.concurrent.Callable<UUID>> calls = new ArrayList<>();
            for (int index = 0; index < 12; index++) calls.add(() -> bootstrap.bootstrap(user).workspaceId());
            var results = executor.invokeAll(calls);
            assertThat(results).allSatisfy(result -> assertThat(result.get()).isNotNull());
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(workspaces.count()).isEqualTo(1);
        assertThat(memberships.count()).isEqualTo(1);
    }

    @Test
    void authenticationAndSecurityLogsDoNotContainSensitiveInputs() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            ResponseEntity<Map> auth = authenticate(730_001L, "LogMarkerUser");
            String accessToken = auth.getBody().get("accessToken").toString();
            String badInitData = "auth_date=1&user=LogMarkerUser&hash=" + "ab".repeat(32);
            assertAuthError(badInitData, "INVALID_TELEGRAM_INIT_DATA");
            getWorkspace(UUID.randomUUID(), accessToken + "tampered");

            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages.stream().noneMatch(message -> message.contains("LogMarkerUser")
                    || message.contains(accessToken) || message.contains(badInitData) || message.contains(TOKEN)))
                    .as("security logs contain no authentication inputs or tokens").isTrue();
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void ownerBotCrudIsIdempotentAndTenantIsolatedThroughRest() {
        Map authA = authenticate(740_001L, "OwnerA").getBody();
        Map authB = authenticate(740_002L, "OwnerB").getBody();
        UUID workspaceA = UUID.fromString(((Map) authA.get("defaultWorkspace")).get("id").toString());
        UUID workspaceB = UUID.fromString(((Map) authB.get("defaultWorkspace")).get("id").toString());
        String tokenA = authA.get("accessToken").toString();
        String tokenB = authB.get("accessToken").toString();

        ResponseEntity<Map> created = createBot(workspaceA, tokenA, "crud-key", "Bot A");
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        UUID botId = UUID.fromString(created.getBody().get("id").toString());
        ResponseEntity<Map> replay = createBot(workspaceA, tokenA, "crud-key", "Bot A");
        assertThat(replay.getStatusCode().value()).isEqualTo(201);
        assertThat(replay.getBody().get("id")).isEqualTo(created.getBody().get("id"));
        assertThat(bots.findAllByWorkspaceId(workspaceA)).hasSize(1);

        HttpHeaders authHeaders = bearer(tokenA);
        ResponseEntity<Map> list = rest.exchange("/api/v1/workspaces/" + workspaceA + "/bots",
                HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) list.getBody().get("items")).hasSize(1);

        ResponseEntity<Map> get = rest.exchange("/api/v1/bots/" + botId, HttpMethod.GET,
                new HttpEntity<>(authHeaders), Map.class);
        assertThat(get.getStatusCode().value()).isEqualTo(200);
        assertThat(get.getBody().toString()).doesNotContain("token").doesNotContain("ciphertext")
                .doesNotContain("webhookSecret").doesNotContain("keyId");

        HttpHeaders patchHeaders = bearer(tokenA);
        patchHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> updated = rest.exchange("/api/v1/bots/" + botId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("displayName", "Renamed"), patchHeaders), Map.class);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody()).containsEntry("displayName", "Renamed");

        assertSecurityError(rest.exchange("/api/v1/bots/" + botId, HttpMethod.GET,
                new HttpEntity<>(bearer(tokenB)), Map.class), 404, "NOT_FOUND");
        assertSecurityError(createBot(workspaceB, tokenA, "foreign-key", "Forbidden"), 404, "NOT_FOUND");
        ResponseEntity<Map> conflict = createBot(workspaceA, tokenA, "crud-key", "Different");
        assertSecurityError(conflict, 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void concurrentIdempotencyKeyCreatesOneLogicalBotWithoutServerError() throws Exception {
        Map auth = authenticate(750_001L, "ConcurrentOwner").getBody();
        UUID workspace = UUID.fromString(((Map) auth.get("defaultWorkspace")).get("id").toString());
        String token = auth.get("accessToken").toString();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Callable<ResponseEntity<Map>>> calls = List.of(
                    () -> { start.await(); return createBot(workspace, token, "parallel-key", "Parallel Bot"); },
                    () -> { start.await(); return createBot(workspace, token, "parallel-key", "Parallel Bot"); });
            var responses = executor.invokeAll(calls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            assertThat(responses).allSatisfy(response -> assertThat(response.getStatusCode().value()).isEqualTo(201));
            assertThat(responses).extracting(response -> response.getBody().get("id")).containsOnly(
                    responses.getFirst().getBody().get("id"));
        }
        assertThat(bots.findAllByWorkspaceId(workspace)).hasSize(1);
        assertThat(idempotencies.findByWorkspaceIdAndIdempotencyKey(workspace, "parallel-key")).isPresent();
    }

    @Test
    void idempotencyIsTenantScopedAndValidationDoesNotCreateRecordAndMemberIsDenied() {
        Map authA = authenticate(760_001L, "OwnerA").getBody();
        Map authB = authenticate(760_002L, "OwnerB").getBody();
        UUID workspaceA = UUID.fromString(((Map) authA.get("defaultWorkspace")).get("id").toString());
        UUID workspaceB = UUID.fromString(((Map) authB.get("defaultWorkspace")).get("id").toString());
        UUID userB = UUID.fromString(((Map) authB.get("user")).get("id").toString());
        String tokenA = authA.get("accessToken").toString();
        String tokenB = authB.get("accessToken").toString();

        assertThat(createBot(workspaceA, tokenA, "shared-key", "A").getStatusCode().value()).isEqualTo(201);
        assertThat(createBot(workspaceB, tokenB, "shared-key", "B").getStatusCode().value()).isEqualTo(201);
        assertThat(idempotencies.count()).isEqualTo(2);

        MembershipEntity member = new MembershipEntity();
        member.setWorkspaceId(workspaceA);
        member.setUserId(userB);
        member.setRole(MembershipRole.MEMBER);
        memberships.saveAndFlush(member);
        assertSecurityError(createBot(workspaceA, tokenB, "member-key", "Denied"), 403, "ACCESS_DENIED");

        HttpHeaders missingKey = bearer(tokenA);
        missingKey.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> invalid = rest.exchange("/api/v1/workspaces/" + workspaceA + "/bots",
                HttpMethod.POST, new HttpEntity<>(Map.of("displayName", "No key"), missingKey), Map.class);
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(idempotencies.count()).isEqualTo(2);
    }

    @Test
    void bootstrapRollsBackCompletelyWhenMembershipPersistenceFailsThenCanRetry() {
        jdbc.execute("""
                create function test_fail_membership() returns trigger language plpgsql as $$
                begin raise exception 'synthetic membership failure'; end $$
                """);
        jdbc.execute("""
                create trigger test_fail_membership before insert on memberships
                for each row execute function test_fail_membership()
                """);
        var telegramUser = new AuthenticatedTelegramUser(770_001L, "rollback", "Rollback", null, "en");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> bootstrap.bootstrap(telegramUser))
                    .isInstanceOf(RuntimeException.class);
            assertThat(users.count()).isZero();
            assertThat(workspaces.count()).isZero();
            assertThat(memberships.count()).isZero();
        } finally {
            jdbc.execute("drop trigger if exists test_fail_membership on memberships");
            jdbc.execute("drop function if exists test_fail_membership()");
        }
        assertThat(bootstrap.bootstrap(telegramUser).workspaceId()).isNotNull();
        assertThat(users.count()).isEqualTo(1);
        assertThat(workspaces.count()).isEqualTo(1);
        assertThat(memberships.count()).isEqualTo(1);
    }

    private ResponseEntity<Map> authenticate(long telegramId, String firstName) {
        String user = "{\"id\":" + telegramId + ",\"first_name\":\"" + firstName + "\"}";
        String initData = TestInitDataSigner.sign(TOKEN, Instant.now().getEpochSecond(), user);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/auth/telegram/session", HttpMethod.POST,
                new HttpEntity<>(Map.of("initData", initData), headers), Map.class);
    }

    private ResponseEntity<Map> getWorkspace(UUID workspaceId, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange("/api/v1/workspaces/" + workspaceId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    private ResponseEntity<Map> createBot(UUID workspaceId, String token, String key, String name) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return rest.exchange("/api/v1/workspaces/" + workspaceId + "/bots", HttpMethod.POST,
                new HttpEntity<>(Map.of("displayName", name), headers), Map.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void assertAuthError(String initData, String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = rest.exchange("/api/v1/auth/telegram/session", HttpMethod.POST,
                new HttpEntity<>(Map.of("initData", initData), headers), Map.class);
        assertSecurityError(response, 401, code);
        assertThat(response.getBody().toString()).doesNotContain(initData).doesNotContain(TOKEN);
    }

    private void assertSecurityError(ResponseEntity<Map> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", code).containsKey("traceId");
        assertThat((Iterable<?>) response.getBody().get("fieldErrors")).isEmpty();
        assertThat(response.getBody().toString()).doesNotContain("stackTrace").doesNotContain("JwtException");
    }

    private BotEntity bot(UUID workspaceId, String name) {
        BotEntity entity = new BotEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setDisplayName(name);
        return entity;
    }

    private String expiredToken(UUID userId) {
        Instant expiresAt = Instant.now().minusSeconds(120);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://bot-constructor.test.invalid")
                .audience(List.of("bot-constructor-test-api"))
                .issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt)
                .subject(userId.toString()).id(UUID.randomUUID().toString()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}

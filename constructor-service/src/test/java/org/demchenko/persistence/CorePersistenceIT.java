package org.demchenko.persistence;

import org.demchenko.bot.data.BotRepository;
import org.demchenko.bot.model.BotEntity;
import org.demchenko.identity.data.repo.PlatformUserRepository;
import org.demchenko.identity.model.PlatformUserEntity;
import org.demchenko.workspace.data.MembershipRepository;
import org.demchenko.workspace.data.WorkspaceRepository;
import org.demchenko.workspace.model.MembershipEntity;
import org.demchenko.workspace.model.MembershipRole;
import org.demchenko.workspace.model.WorkspaceEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional
class CorePersistenceIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-bookworm")
            .withDatabaseName("constructor_test")
            .withUsername("constructor_test")
            .withPassword("obvious_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformUserRepository userRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private BotRepository botRepository;

    @Test
    void migrationsCreateExpectedSchemaAndAreIdempotent() {
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        ));

        assertThat(tables).contains(
                "flyway_schema_history", "platform_users", "workspaces",
                "memberships", "bots", "user_data"
                , "bot_credentials", "bot_idempotency_records",
                "bot_credential_operations", "bot_credential_history"
        );
        assertThat(flyway.info().applied()).hasSize(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void userPersistenceAndTelegramIdentityConstraintsWork() {
        PlatformUserEntity user = user(100_001L, null);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByTelegramUserId(100_001L)).contains(user);
        assertThat(userRepository.existsByTelegramUserId(100_001L)).isTrue();
        assertThat(user.getTelegramUsername()).isNull();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(100_001L, "duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void workspaceOwnerMustExist() {
        assertThatThrownBy(() -> workspaceRepository.saveAndFlush(workspace(UUID.randomUUID(), "Invalid")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void membershipForeignKeysUniquenessRoleAndLookupWork() {
        PlatformUserEntity owner = userRepository.saveAndFlush(user(200_001L, "owner"));
        PlatformUserEntity member = userRepository.saveAndFlush(user(200_002L, null));
        WorkspaceEntity workspace = workspaceRepository.saveAndFlush(workspace(owner.getId(), "Primary"));

        MembershipEntity membership = membership(workspace.getId(), member.getId(), MembershipRole.MEMBER);
        membershipRepository.saveAndFlush(membership);

        assertThat(membershipRepository.findAllByUserId(member.getId()))
                .extracting(MembershipEntity::getId).containsExactly(membership.getId());
        assertThat(membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), member.getId()))
                .contains(membership);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM memberships WHERE id = ?", String.class, membership.getId()))
                .isEqualTo("MEMBER");

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(
                membership(workspace.getId(), member.getId(), MembershipRole.OWNER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void membershipRejectsUnknownUser() {
        PlatformUserEntity user = userRepository.saveAndFlush(user(300_001L, null));
        WorkspaceEntity workspace = workspaceRepository.saveAndFlush(workspace(user.getId(), "FK checks"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO memberships (id, workspace_id, user_id, role) VALUES (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(), workspace.getId(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void membershipRejectsUnknownWorkspace() {
        PlatformUserEntity user = userRepository.saveAndFlush(user(300_002L, null));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO memberships (id, workspace_id, user_id, role) VALUES (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(), UUID.randomUUID(), user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void membershipRejectsUnknownRole() {
        PlatformUserEntity user = userRepository.saveAndFlush(user(300_003L, null));
        WorkspaceEntity workspace = workspaceRepository.saveAndFlush(workspace(user.getId(), "Role check"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO memberships (id, workspace_id, user_id, role) VALUES (?, ?, ?, 'ADMIN')",
                UUID.randomUUID(), workspace.getId(), user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void botTenantOwnershipLookupNullableOnboardingAndUniquenessWork() {
        PlatformUserEntity owner = userRepository.saveAndFlush(user(400_001L, null));
        WorkspaceEntity workspace = workspaceRepository.saveAndFlush(workspace(owner.getId(), "Bots"));

        BotEntity connected = bot(workspace.getId(), "Connected", 900_001L);
        BotEntity pendingOne = bot(workspace.getId(), "Pending one", null);
        BotEntity pendingTwo = bot(workspace.getId(), "Pending two", null);
        botRepository.saveAllAndFlush(List.of(connected, pendingOne, pendingTwo));

        assertThat(botRepository.findAllByWorkspaceId(workspace.getId())).hasSize(3);
        assertThat(botRepository.findByTelegramBotId(900_001L)).contains(connected);

        assertThatThrownBy(() -> botRepository.saveAndFlush(bot(workspace.getId(), "Duplicate", 900_001L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void botCannotExistWithoutValidWorkspaceAndHasNoTokenColumn() {
        Integer tokenColumns = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'bots'
                  AND column_name ILIKE '%token%'
                """, Integer.class);
        assertThat(tokenColumns).isZero();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO bots (id, workspace_id, display_name) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "Orphan"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void botRejectsNullWorkspace() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO bots (id, workspace_id, display_name) VALUES (?, NULL, ?)",
                UUID.randomUUID(), "Tenantless"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PlatformUserEntity user(long telegramUserId, String username) {
        PlatformUserEntity entity = new PlatformUserEntity();
        entity.setTelegramUserId(telegramUserId);
        entity.setTelegramUsername(username);
        return entity;
    }

    private WorkspaceEntity workspace(UUID ownerId, String name) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setOwnerUserId(ownerId);
        entity.setName(name);
        return entity;
    }

    private MembershipEntity membership(UUID workspaceId, UUID userId, MembershipRole role) {
        MembershipEntity entity = new MembershipEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setUserId(userId);
        entity.setRole(role);
        return entity;
    }

    private BotEntity bot(UUID workspaceId, String name, Long telegramBotId) {
        BotEntity entity = new BotEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setDisplayName(name);
        entity.setTelegramBotId(telegramBotId);
        return entity;
    }
}

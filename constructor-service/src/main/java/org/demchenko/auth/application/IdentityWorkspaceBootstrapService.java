package org.demchenko.auth.application;

import lombok.RequiredArgsConstructor;
import org.demchenko.identity.data.repo.PlatformUserRepository;
import org.demchenko.identity.model.PlatformUserEntity;
import org.demchenko.workspace.data.MembershipRepository;
import org.demchenko.workspace.data.WorkspaceRepository;
import org.demchenko.workspace.model.MembershipEntity;
import org.demchenko.workspace.model.MembershipRole;
import org.demchenko.workspace.model.WorkspaceEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class IdentityWorkspaceBootstrapService {
    private final JdbcTemplate jdbcTemplate;
    private final PlatformUserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public AuthenticatedPlatformContext bootstrap(AuthenticatedTelegramUser telegramUser) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, telegramUser.telegramUserId());
                statement.execute();
            }
            return null;
        });

        PlatformUserEntity existingUser = userRepository.findByTelegramUserId(telegramUser.telegramUserId())
                .orElseGet(() -> newUser(telegramUser));
        updateProfile(existingUser, telegramUser);
        PlatformUserEntity user = userRepository.saveAndFlush(existingUser);

        WorkspaceEntity existingWorkspace = workspaceRepository.findByOwnerUserIdAndDefaultWorkspaceTrue(user.getId())
                .orElseGet(() -> newDefaultWorkspace(user));
        WorkspaceEntity workspace = workspaceRepository.saveAndFlush(existingWorkspace);

        MembershipEntity membership = membershipRepository.findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseGet(() -> newOwnerMembership(workspace, user));
        if (membership.getRole() != MembershipRole.OWNER) {
            membership.setRole(MembershipRole.OWNER);
        }
        membershipRepository.saveAndFlush(membership);

        return new AuthenticatedPlatformContext(user.getId(), displayName(user), workspace.getId(),
                workspace.getName(), MembershipRole.OWNER.name());
    }

    private PlatformUserEntity newUser(AuthenticatedTelegramUser source) {
        PlatformUserEntity user = new PlatformUserEntity();
        user.setTelegramUserId(source.telegramUserId());
        return user;
    }

    private void updateProfile(PlatformUserEntity user, AuthenticatedTelegramUser source) {
        if (source.username() != null) user.setTelegramUsername(source.username());
        if (source.firstName() != null) user.setFirstName(source.firstName());
        if (source.lastName() != null) user.setLastName(source.lastName());
    }

    private WorkspaceEntity newDefaultWorkspace(PlatformUserEntity owner) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setOwnerUserId(owner.getId());
        workspace.setName("My Workspace");
        workspace.setDefaultWorkspace(true);
        return workspace;
    }

    private MembershipEntity newOwnerMembership(WorkspaceEntity workspace, PlatformUserEntity user) {
        MembershipEntity membership = new MembershipEntity();
        membership.setWorkspaceId(workspace.getId());
        membership.setUserId(user.getId());
        membership.setRole(MembershipRole.OWNER);
        return membership;
    }

    private String displayName(PlatformUserEntity user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isEmpty() ? null : fullName;
    }
}

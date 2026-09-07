package org.demchenko.auth.application;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.workspace.data.MembershipRepository;
import org.demchenko.workspace.data.WorkspaceRepository;
import org.demchenko.workspace.model.MembershipRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class TenantAccessPolicy {
    private final WorkspaceRepository workspaceRepository;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public WorkspaceAccessResult requireMembership(UUID userId, UUID workspaceId) {
        var workspace = workspaceRepository.findAccessibleByIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new SafeApiException(404, "NOT_FOUND", "Resource was not found"));
        var membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new SafeApiException(404, "NOT_FOUND", "Resource was not found"));
        return new WorkspaceAccessResult(workspace.getId(), workspace.getName(), membership.getRole().name());
    }

    @Transactional(readOnly = true)
    public WorkspaceAccessResult requireOwner(UUID userId, UUID workspaceId) {
        WorkspaceAccessResult result = requireMembership(userId, workspaceId);
        if (!MembershipRole.OWNER.name().equals(result.role())) throw new SafeApiException(403, "ACCESS_DENIED", "Access is denied");
        return result;
    }
}

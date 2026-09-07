package org.demchenko.auth.web;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.generated.api.WorkspaceAccessApi;
import org.demchenko.api.generated.model.WorkspaceSummary;
import org.demchenko.auth.application.TenantAccessPolicy;
import org.demchenko.auth.security.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkspaceAccessController implements WorkspaceAccessApi {
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<TenantAccessPolicy> accessPolicy;
    private final WorkspaceAccessMapper mapper;

    @Override
    public ResponseEntity<WorkspaceSummary> getWorkspace(UUID workspaceId) {
        return ResponseEntity.ok(mapper.toResponse(
                accessPolicy.getObject().requireMembership(currentUserProvider.requireUserId(), workspaceId)));
    }
}

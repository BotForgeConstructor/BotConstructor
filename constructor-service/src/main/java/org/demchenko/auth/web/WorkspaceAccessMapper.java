package org.demchenko.auth.web;

import org.demchenko.api.generated.model.WorkspaceSummary;
import org.demchenko.auth.application.WorkspaceAccessResult;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccessMapper {
    public WorkspaceSummary toResponse(WorkspaceAccessResult result) {
        WorkspaceSummary response = new WorkspaceSummary();
        response.setId(result.id());
        response.setName(result.name());
        response.setRole(org.demchenko.api.generated.model.MembershipRole.fromValue(result.role()));
        return response;
    }
}

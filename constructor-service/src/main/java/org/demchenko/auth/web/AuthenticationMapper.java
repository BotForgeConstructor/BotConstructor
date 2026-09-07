package org.demchenko.auth.web;

import org.demchenko.api.generated.model.AuthResponse;
import org.demchenko.api.generated.model.UserSummary;
import org.demchenko.api.generated.model.WorkspaceSummary;
import org.demchenko.auth.application.AuthenticationResult;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class AuthenticationMapper {
    public AuthResponse toResponse(AuthenticationResult result) {
        var context = result.context();
        UserSummary user = new UserSummary();
        user.setId(context.userId());
        user.setDisplayName(context.displayName());
        WorkspaceSummary workspace = new WorkspaceSummary();
        workspace.setId(context.workspaceId());
        workspace.setName(context.workspaceName());
        workspace.setRole(org.demchenko.api.generated.model.MembershipRole.fromValue(context.role()));
        AuthResponse response = new AuthResponse();
        response.setAccessToken(result.session().accessToken());
        response.setTokenType(AuthResponse.TokenTypeEnum.BEARER);
        response.setExpiresIn(result.session().expiresInSeconds());
        response.setExpiresAt(OffsetDateTime.ofInstant(result.session().expiresAt(), ZoneOffset.UTC));
        response.setUser(user);
        response.setDefaultWorkspace(workspace);
        return response;
    }
}

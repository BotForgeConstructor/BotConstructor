package org.demchenko.auth.application;

import java.util.UUID;

public record AuthenticatedPlatformContext(
        UUID userId,
        String displayName,
        UUID workspaceId,
        String workspaceName,
        String role
) {
}

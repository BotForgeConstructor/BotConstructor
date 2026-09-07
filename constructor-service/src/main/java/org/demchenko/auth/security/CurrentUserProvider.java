package org.demchenko.auth.security;

import org.demchenko.api.application.error.SafeApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {
    public UUID requireUserId() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw new SafeApiException(401, "AUTHENTICATION_REQUIRED", "Authentication is required");
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (RuntimeException exception) {
            throw new SafeApiException(401, "INVALID_SESSION", "Authentication is required");
        }
    }
}

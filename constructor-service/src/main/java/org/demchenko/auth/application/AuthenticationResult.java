package org.demchenko.auth.application;

public record AuthenticationResult(AuthenticatedPlatformContext context, PlatformSession session) { }

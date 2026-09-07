package org.demchenko.auth.application;

import java.time.Instant;

public record PlatformSession(String accessToken, Instant expiresAt, long expiresInSeconds) { }

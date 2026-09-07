package org.demchenko.auth.application;

import java.util.UUID;

public record WorkspaceAccessResult(UUID id, String name, String role) { }

package org.demchenko.auth.application;

import java.util.UUID;

public interface PlatformSessionIssuer {
    PlatformSession issue(UUID userId);
}

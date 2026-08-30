package org.demchenko.api.application.status;

import org.springframework.stereotype.Service;

@Service
public class PlatformStatusService {

    public PlatformStatus getStatus() {
        String implementationVersion = PlatformStatusService.class.getPackage().getImplementationVersion();
        String applicationVersion = implementationVersion == null ? "development" : implementationVersion;
        return new PlatformStatus("UP", "v1", applicationVersion);
    }
}

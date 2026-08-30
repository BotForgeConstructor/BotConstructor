package org.demchenko.api.web.status;

import org.demchenko.api.application.status.PlatformStatus;
import org.demchenko.api.generated.model.StatusResponse;
import org.springframework.stereotype.Component;

@Component
public class PlatformStatusMapper {

    public StatusResponse toResponse(PlatformStatus status) {
        return new StatusResponse(
                StatusResponse.StatusEnum.fromValue(status.status()),
                StatusResponse.ApiVersionEnum.fromValue(status.apiVersion()),
                status.applicationVersion()
        );
    }
}

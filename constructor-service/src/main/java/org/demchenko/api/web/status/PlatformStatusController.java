package org.demchenko.api.web.status;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.status.PlatformStatusService;
import org.demchenko.api.generated.api.StatusApi;
import org.demchenko.api.generated.model.StatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlatformStatusController implements StatusApi {

    private final PlatformStatusService statusService;
    private final PlatformStatusMapper mapper;

    @Override
    public ResponseEntity<StatusResponse> getPlatformStatus() {
        return ResponseEntity.ok(mapper.toResponse(statusService.getStatus()));
    }
}

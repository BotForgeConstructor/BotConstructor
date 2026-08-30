package org.demchenko.api.web.status;

import org.demchenko.api.application.status.PlatformStatus;
import org.demchenko.api.generated.model.StatusResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformStatusMapperTest {

    private final PlatformStatusMapper mapper = new PlatformStatusMapper();

    @Test
    void mapsEveryApplicationFieldToGeneratedDto() {
        StatusResponse response = mapper.toResponse(new PlatformStatus("UP", "v1", "1.0-test"));

        assertThat(response.getStatus()).isEqualTo(StatusResponse.StatusEnum.UP);
        assertThat(response.getApiVersion()).isEqualTo(StatusResponse.ApiVersionEnum.V1);
        assertThat(response.getApplicationVersion()).isEqualTo("1.0-test");
    }
}

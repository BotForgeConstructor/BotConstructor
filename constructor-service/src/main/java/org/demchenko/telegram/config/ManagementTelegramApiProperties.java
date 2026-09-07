package org.demchenko.telegram.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.telegram.api")
public record ManagementTelegramApiProperties(@NotBlank String baseUrl, Duration connectTimeout,
                                              Duration readTimeout) {
    public ManagementTelegramApiProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}

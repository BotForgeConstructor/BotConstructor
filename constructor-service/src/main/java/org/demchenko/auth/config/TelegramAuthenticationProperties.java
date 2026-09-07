package org.demchenko.auth.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.auth.telegram")
public record TelegramAuthenticationProperties(
        @NotNull Duration maxInitDataAge,
        @NotNull Duration allowedFutureSkew
) {
    public TelegramAuthenticationProperties {
        if (maxInitDataAge == null || maxInitDataAge.isNegative() || maxInitDataAge.isZero()
                || allowedFutureSkew == null || allowedFutureSkew.isNegative()) {
            throw new IllegalArgumentException("Telegram authentication timing configuration is invalid");
        }
    }
}

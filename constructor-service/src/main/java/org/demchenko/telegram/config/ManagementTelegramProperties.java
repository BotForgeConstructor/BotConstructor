package org.demchenko.telegram.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.telegram.management")
public record ManagementTelegramProperties(
        boolean enabled,
        ConnectionMode connectionMode,
        String token,
        String username
) {
    private static final String TOKEN_PATTERN = "[0-9]{6,12}:[A-Za-z0-9_-]{20,}";
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_]{5,32}";

    public ManagementTelegramProperties {
        connectionMode = connectionMode == null ? ConnectionMode.LONG_POLLING : connectionMode;
    }

    @AssertTrue(message = "management Telegram credentials are missing or malformed")
    public boolean isConfigurationValid() {
        if (!enabled) {
            return true;
        }
        return token != null && token.matches(TOKEN_PATTERN)
                && username != null && username.matches(USERNAME_PATTERN);
    }

    public enum ConnectionMode {
        LONG_POLLING,
        WEBHOOK
    }
}

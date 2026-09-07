package org.demchenko.bot.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("app.bot.webhook")
public record BotWebhookProperties(@NotNull URI baseUrl) {
    @AssertTrue(message = "bot webhook base URL must use HTTPS (HTTP is allowed only for loopback development)")
    public boolean isSecure() {
        if (baseUrl == null) {
            return true;
        }
        if ("https".equalsIgnoreCase(baseUrl.getScheme())) {
            return true;
        }
        String host = baseUrl.getHost();
        return "http".equalsIgnoreCase(baseUrl.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
    }
}

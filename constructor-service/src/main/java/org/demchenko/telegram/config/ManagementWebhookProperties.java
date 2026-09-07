package org.demchenko.telegram.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("app.telegram.management.webhook")
public record ManagementWebhookProperties(boolean enabled, String secret, @NotNull URI path) {
    @AssertTrue(message = "management webhook secret is required when webhook is enabled")
    public boolean isValidWhenEnabled() {
        return !enabled || (secret != null && secret.matches("[A-Za-z0-9_-]{1,256}")
                && path != null && !path.isAbsolute() && path.getQuery() == null && path.getFragment() == null
                && path.toString().startsWith("/"));
    }
}

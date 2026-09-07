package org.demchenko.telegram.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("app.telegram.management.mini-app")
public record ManagementMiniAppProperties(@NotNull URI url, String buttonLabel) {
    public ManagementMiniAppProperties {
        if (buttonLabel == null || buttonLabel.isBlank()) {
            buttonLabel = "Open Bot Builder";
        }
    }

    @AssertTrue(message = "Mini App URL must use HTTPS (HTTP is allowed only for loopback development)")
    public boolean isSecure() {
        if (url == null) {
            return true;
        }
        if ("https".equalsIgnoreCase(url.getScheme())) {
            return true;
        }
        String host = url.getHost();
        return "http".equalsIgnoreCase(url.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
    }
}

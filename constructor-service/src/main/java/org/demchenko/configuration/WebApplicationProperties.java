package org.demchenko.configuration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties("app.web")
public record WebApplicationProperties(@NotNull URI url) {
    @AssertTrue(message = "public web URL must use HTTPS (HTTP is allowed only for loopback development)")
    public boolean isSecurePublicUrl() {
        return isHttpsOrLoopback(url);
    }

    private static boolean isHttpsOrLoopback(URI value) {
        if (value == null) {
            return true;
        }
        if ("https".equalsIgnoreCase(value.getScheme())) {
            return true;
        }
        String host = value.getHost();
        return "http".equalsIgnoreCase(value.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
    }
}

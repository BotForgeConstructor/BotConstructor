package org.demchenko.auth.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@Validated
@ConfigurationProperties("app.auth.session")
public record PlatformSessionProperties(
        String signingKey,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration ttl
) {
    @AssertTrue(message = "platform session signing configuration is invalid")
    public boolean isValid() {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || signingKey == null) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(signingKey).length >= 32;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public byte[] decodedSigningKey() {
        return Base64.getDecoder().decode(signingKey);
    }

    @Override
    public String toString() {
        return "PlatformSessionProperties[signingKey=<redacted>, issuer=" + issuer
                + ", audience=" + audience + ", ttl=" + ttl + "]";
    }
}

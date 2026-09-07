package org.demchenko.bot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Base64;

@Validated
@ConfigurationProperties("app.security.bot-credentials")
public record CredentialEncryptionProperties(@NotBlank String key, @NotBlank String keyId) {
    public byte[] decodedKey() {
        try { var bytes=Base64.getDecoder().decode(key); if(bytes.length!=32) throw new IllegalStateException("invalid encryption key length"); return bytes; }
        catch (IllegalArgumentException e) { throw new IllegalStateException("invalid encryption key encoding"); }
    }
}

package org.demchenko.bot;

import org.demchenko.bot.config.CredentialEncryptionProperties;
import org.demchenko.bot.infrastructure.AesGcmBotCredentialCipher;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class BotCredentialCipherTest {
    private final AesGcmBotCredentialCipher cipher = new AesGcmBotCredentialCipher(
            new CredentialEncryptionProperties(Base64.getEncoder().encodeToString(new byte[32]), "test-v1"));

    @Test
    void encryptsWithAuthenticatedRandomEnvelopeAndRoundTrips() {
        String first = cipher.encrypt("test-token-marker", "bot:token");
        String second = cipher.encrypt("test-token-marker", "bot:token");
        assertThat(first).isNotEqualTo(second).doesNotContain("test-token-marker");
        assertThat(cipher.decrypt(first, "bot:token")).isEqualTo("test-token-marker");
        assertThatThrownBy(() -> cipher.decrypt(first.substring(0, first.length() - 1) + "x", "bot:token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credential decryption failed");
        assertThatThrownBy(() -> cipher.decrypt(first, "other-context"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credential decryption failed");
    }

    @Test
    void rejectsWrongKeyVersionKeyIdIvCiphertextAndTagWithoutSensitiveDetails() {
        String plaintext = "credential-plaintext-marker";
        String envelope = cipher.encrypt(plaintext, "bot-a:token");
        AesGcmBotCredentialCipher wrongKey = new AesGcmBotCredentialCipher(
                new CredentialEncryptionProperties(Base64.getEncoder().encodeToString(
                        "B".repeat(32).getBytes(StandardCharsets.UTF_8)), "test-v1"));

        assertSafeFailure(() -> wrongKey.decrypt(envelope, "bot-a:token"), plaintext);
        assertSafeFailure(() -> cipher.decrypt(envelope.replaceFirst("v1:", "v2:"), "bot-a:token"), plaintext);
        assertSafeFailure(() -> cipher.decrypt(envelope.replaceFirst("test-v1", "unknown-key"),
                "bot-a:token"), plaintext);

        String[] parts = envelope.split(":");
        assertSafeFailure(() -> cipher.decrypt(String.join(":", parts[0], parts[1], mutate(parts[2]), parts[3]),
                "bot-a:token"), plaintext);
        assertSafeFailure(() -> cipher.decrypt(String.join(":", parts[0], parts[1], parts[2], mutate(parts[3])),
                "bot-a:token"), plaintext);
        assertThat(envelope).doesNotContain(plaintext);
    }

    @Test
    void validatesKeyLengthWithoutEchoingKeyMaterial() {
        String invalid = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> new AesGcmBotCredentialCipher(
                new CredentialEncryptionProperties(invalid, "test-v1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid encryption key length")
                .hasMessageNotContaining(invalid);
    }

    private void assertSafeFailure(Runnable action, String plaintext) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("credential decryption failed")
                .hasMessageNotContaining(plaintext);
    }

    private String mutate(String encoded) {
        char first = encoded.charAt(0);
        return (first == 'A' ? 'B' : 'A') + encoded.substring(1);
    }
}

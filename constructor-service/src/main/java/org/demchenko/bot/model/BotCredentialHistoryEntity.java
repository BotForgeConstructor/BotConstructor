package org.demchenko.bot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bot_credential_history")
@Getter
@Setter
@NoArgsConstructor
public class BotCredentialHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "bot_id", nullable = false)
    private UUID botId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "encrypted_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedToken;

    @Column(name = "encrypted_webhook_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedWebhookSecret;

    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;

    @Column(name = "webhook_key_id", nullable = false, length = 64)
    private String webhookKeyId;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "replaced_at", nullable = false)
    private Instant replacedAt;
}

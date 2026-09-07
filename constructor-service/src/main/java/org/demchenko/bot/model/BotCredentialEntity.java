package org.demchenko.bot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bot_credentials")
@Getter @Setter @NoArgsConstructor
public class BotCredentialEntity {
    @Id
    @Column(name = "bot_id")
    private UUID botId;
    @Column(name = "encrypted_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedToken;
    @Column(name = "encrypted_webhook_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedWebhookSecret;
    @Column(name = "token_key_id", nullable = false, length = 64)
    private String tokenKeyId;
    @Column(name = "webhook_key_id", nullable = false, length = 64)
    private String webhookKeyId;
    @Column(name = "operation_id")
    private UUID operationId;
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version @Column(nullable = false)
    private long version;
    @PrePersist void create() { var now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void update() { updatedAt=Instant.now(); }
}

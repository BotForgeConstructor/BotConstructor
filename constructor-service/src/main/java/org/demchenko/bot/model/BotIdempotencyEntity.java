package org.demchenko.bot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="bot_idempotency_records", uniqueConstraints=@UniqueConstraint(name="uk_bot_idempotency_workspace_key", columnNames={"workspace_id","idempotency_key"}))
@Getter @Setter @NoArgsConstructor
public class BotIdempotencyEntity {
 @Id private UUID id;
 @Column(name="workspace_id", nullable=false) private UUID workspaceId;
 @Column(name="idempotency_key", nullable=false, length=128) private String idempotencyKey;
 @Column(name="request_fingerprint", nullable=false, length=64) private String requestFingerprint;
 @Column(name="bot_id", nullable=false) private UUID botId;
 @Column(name="created_at", nullable=false) private Instant createdAt;
}

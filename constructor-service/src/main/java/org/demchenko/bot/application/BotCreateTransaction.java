package org.demchenko.bot.application;

import lombok.RequiredArgsConstructor;
import org.demchenko.bot.data.BotIdempotencyRepository;
import org.demchenko.bot.data.BotRepository;
import org.demchenko.bot.model.BotEntity;
import org.demchenko.bot.model.BotIdempotencyEntity;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BotCreateTransaction {
    private final BotRepository bots;
    private final BotIdempotencyRepository idempotencies;

    @Transactional
    public BotEntity create(UUID workspaceId, String key, String fingerprint, String name) {
        var existing = idempotencies.findByWorkspaceIdAndIdempotencyKey(workspaceId, key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new org.demchenko.api.application.error.SafeApiException(409, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used");
            }
            return bots.findById(existing.getBotId()).orElseThrow();
        }
        var bot = new BotEntity();
        bot.setWorkspaceId(workspaceId);
        bot.setDisplayName(name);
        bot = bots.saveAndFlush(bot);
        var record = new BotIdempotencyEntity();
        record.setId(UUID.randomUUID());
        record.setWorkspaceId(workspaceId);
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setBotId(bot.getId());
        record.setCreatedAt(Instant.now());
        idempotencies.saveAndFlush(record);
        return bot;
    }
}

package org.demchenko.bot.data;

import org.demchenko.bot.model.BotCredentialHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BotCredentialHistoryRepository extends JpaRepository<BotCredentialHistoryEntity, UUID> {
    long countByBotId(UUID botId);
}

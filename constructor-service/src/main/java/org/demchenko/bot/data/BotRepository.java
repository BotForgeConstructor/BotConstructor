package org.demchenko.bot.data;

import org.demchenko.bot.model.BotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository extends JpaRepository<BotEntity, UUID> {
    List<BotEntity> findAllByWorkspaceId(UUID workspaceId);

    Optional<BotEntity> findByTelegramBotId(long telegramBotId);
}

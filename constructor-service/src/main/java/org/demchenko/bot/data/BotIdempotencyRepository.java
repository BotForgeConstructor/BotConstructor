package org.demchenko.bot.data;
import org.demchenko.bot.model.BotIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BotIdempotencyRepository extends JpaRepository<BotIdempotencyEntity, UUID> {
 Optional<BotIdempotencyEntity> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String key);
}

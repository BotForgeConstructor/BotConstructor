package org.demchenko.bot.data;

import org.demchenko.bot.model.BotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository extends JpaRepository<BotEntity, UUID> {
    List<BotEntity> findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(UUID workspaceId);
    List<BotEntity> findAllByWorkspaceId(UUID workspaceId);

    Optional<BotEntity> findByTelegramBotId(long telegramBotId);

    Optional<BotEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bot from BotEntity bot where bot.id = :id")
    Optional<BotEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bot from BotEntity bot where bot.id = :id and bot.workspaceId = :workspaceId")
    Optional<BotEntity> findByIdAndWorkspaceIdForUpdate(@Param("id") UUID id,
                                                        @Param("workspaceId") UUID workspaceId);

    @Query(value = """
            select bot.* from bots bot
            join memberships membership on membership.workspace_id = bot.workspace_id
            where bot.id = :id and membership.user_id = :userId
            """, nativeQuery = true)
    Optional<BotEntity> findAccessibleByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bot from BotEntity bot
            where bot.id = :id and exists (
                select membership.id from MembershipEntity membership
                where membership.workspaceId = bot.workspaceId and membership.userId = :userId
            )
            """)
    Optional<BotEntity> findAccessibleByIdAndUserIdForUpdate(@Param("id") UUID id,
                                                             @Param("userId") UUID userId);

    @Query(value = "select pg_advisory_xact_lock(:telegramBotId)", nativeQuery = true)
    void lockTelegramBotId(@Param("telegramBotId") long telegramBotId);
}

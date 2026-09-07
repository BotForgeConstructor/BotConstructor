package org.demchenko.bot.data;

import jakarta.persistence.LockModeType;
import org.demchenko.bot.model.BotCredentialOperationEntity;
import org.demchenko.bot.model.CredentialOperationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BotCredentialOperationRepository extends JpaRepository<BotCredentialOperationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from BotCredentialOperationEntity operation where operation.id = :id")
    Optional<BotCredentialOperationEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<BotCredentialOperationEntity> findFirstByBotIdAndStateInOrderByCreatedAtDesc(
            UUID botId, Collection<CredentialOperationState> states);
}

package org.demchenko.identity.data.repo;

import org.demchenko.identity.model.PlatformUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, UUID> {
    Optional<PlatformUserEntity> findByTelegramUserId(long telegramUserId);

    boolean existsByTelegramUserId(long telegramUserId);
}

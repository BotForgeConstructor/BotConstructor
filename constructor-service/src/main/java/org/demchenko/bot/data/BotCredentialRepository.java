package org.demchenko.bot.data;

import org.demchenko.bot.model.BotCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BotCredentialRepository extends JpaRepository<BotCredentialEntity, UUID> { }

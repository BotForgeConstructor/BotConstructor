package org.demchenko.bot.application;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.auth.application.TenantAccessPolicy;
import org.demchenko.bot.config.CredentialEncryptionProperties;
import org.demchenko.bot.data.BotCredentialHistoryRepository;
import org.demchenko.bot.data.BotCredentialOperationRepository;
import org.demchenko.bot.data.BotCredentialRepository;
import org.demchenko.bot.data.BotRepository;
import org.demchenko.bot.model.BotCredentialEntity;
import org.demchenko.bot.model.BotCredentialHistoryEntity;
import org.demchenko.bot.model.BotCredentialOperationEntity;
import org.demchenko.bot.model.BotEntity;
import org.demchenko.bot.model.CredentialOperationState;
import org.demchenko.bot.model.CredentialOperationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BotCredentialPersistenceService {
    private static final EnumSet<CredentialOperationState> IN_PROGRESS = EnumSet.of(
            CredentialOperationState.PENDING_VERIFICATION,
            CredentialOperationState.WEBHOOK_PENDING);

    private final BotRepository bots;
    private final BotCredentialRepository credentials;
    private final BotCredentialOperationRepository operations;
    private final BotCredentialHistoryRepository history;
    private final TenantAccessPolicy access;
    private final BotCredentialCipher cipher;
    private final CredentialEncryptionProperties encryptionProperties;

    @Transactional
    public OnboardingAttempt prepareConnect(UUID userId, UUID botId, String token, String webhookSecret) {
        BotEntity bot = accessibleBotForUpdate(botId, userId);
        access.requireOwner(userId, bot.getWorkspaceId());
        BotCredentialOperationEntity existing = operations
                .findFirstByBotIdAndStateInOrderByCreatedAtDesc(botId, IN_PROGRESS).orElse(null);
        if (existing != null) {
            String existingToken = decrypt(existing.getEncryptedToken(), tokenAad(botId));
            if (!MessageDigest.isEqual(existingToken.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8))) {
                throw new SafeApiException(409, "ONBOARDING_IN_PROGRESS",
                        "Bot credential onboarding is already in progress");
            }
            return attempt(existing, activeSnapshot(botId));
        }

        BotCredentialEntity active = credentials.findById(botId).orElse(null);
        BotCredentialOperationEntity operation = new BotCredentialOperationEntity();
        operation.setId(UUID.randomUUID());
        operation.setBotId(botId);
        operation.setOperationType(active == null
                ? CredentialOperationType.CONNECT : CredentialOperationType.REPLACE);
        operation.setState(CredentialOperationState.PENDING_VERIFICATION);
        operation.setEncryptedToken(cipher.encrypt(token, tokenAad(botId)));
        operation.setEncryptedWebhookSecret(cipher.encrypt(webhookSecret, secretAad(botId)));
        operation.setTokenKeyId(encryptionProperties.keyId());
        operation.setWebhookKeyId(encryptionProperties.keyId());
        operations.saveAndFlush(operation);
        return attempt(operation, activeSnapshot(active));
    }

    @Transactional
    public OnboardingAttempt prepareReconnect(UUID userId, UUID botId) {
        BotEntity bot = accessibleBotForUpdate(botId, userId);
        access.requireOwner(userId, bot.getWorkspaceId());
        BotCredentialOperationEntity existing = operations
                .findFirstByBotIdAndStateInOrderByCreatedAtDesc(botId, IN_PROGRESS).orElse(null);
        if (existing != null) {
            if (existing.getOperationType() != CredentialOperationType.RECONNECT) {
                throw new SafeApiException(409, "ONBOARDING_IN_PROGRESS",
                        "Bot credential onboarding is already in progress");
            }
            return attempt(existing, activeSnapshot(botId));
        }
        BotCredentialEntity active = credentials.findById(botId)
                .orElseThrow(this::notFound);
        BotCredentialOperationEntity operation = new BotCredentialOperationEntity();
        operation.setId(UUID.randomUUID());
        operation.setBotId(botId);
        operation.setOperationType(CredentialOperationType.RECONNECT);
        operation.setState(CredentialOperationState.PENDING_VERIFICATION);
        operation.setEncryptedToken(active.getEncryptedToken());
        operation.setEncryptedWebhookSecret(active.getEncryptedWebhookSecret());
        operation.setTokenKeyId(active.getTokenKeyId());
        operation.setWebhookKeyId(active.getWebhookKeyId());
        operations.saveAndFlush(operation);
        return attempt(operation, activeSnapshot(active));
    }

    @Transactional
    public OnboardingAttempt markVerified(UUID operationId, TelegramBotGateway.VerifiedBot verified) {
        BotCredentialOperationEntity operation = operationForUpdate(operationId);
        if (operation.getState() == CredentialOperationState.COMPLETED
                || operation.getState() == CredentialOperationState.WEBHOOK_PENDING) {
            return attempt(operation, activeSnapshot(operation.getBotId()));
        }
        if (operation.getState() != CredentialOperationState.PENDING_VERIFICATION) {
            throw onboardingConflict();
        }
        bots.lockTelegramBotId(verified.id());
        if (bots.findByTelegramBotId(verified.id())
                .filter(bot -> !bot.getId().equals(operation.getBotId())).isPresent()) {
            throw new SafeApiException(409, "BOT_ALREADY_CONNECTED",
                    "Telegram bot is already connected");
        }
        operation.setTelegramBotId(verified.id());
        operation.setTelegramUsername(verified.username());
        operation.setFailureCode(null);
        operation.setState(CredentialOperationState.WEBHOOK_PENDING);
        operations.saveAndFlush(operation);
        return attempt(operation, activeSnapshot(operation.getBotId()));
    }

    @Transactional
    public BotApplicationService.BotCredentialResult activate(UUID operationId) {
        BotCredentialOperationEntity operation = operationForUpdate(operationId);
        BotEntity bot = bots.findByIdForUpdate(operation.getBotId()).orElseThrow(this::notFound);
        if (operation.getState() == CredentialOperationState.COMPLETED) {
            return result(bot);
        }
        if (operation.getState() != CredentialOperationState.WEBHOOK_PENDING
                || operation.getTelegramBotId() == null) {
            throw onboardingConflict();
        }
        bots.lockTelegramBotId(operation.getTelegramBotId());
        if (bots.findByTelegramBotId(operation.getTelegramBotId())
                .filter(found -> !found.getId().equals(bot.getId())).isPresent()) {
            throw new SafeApiException(409, "BOT_ALREADY_CONNECTED",
                    "Telegram bot is already connected");
        }

        if (operation.getOperationType() != CredentialOperationType.RECONNECT) {
            BotCredentialEntity active = credentials.findById(bot.getId()).orElse(null);
            if (active != null) {
                history.save(replaced(active, operationId));
            } else {
                active = new BotCredentialEntity();
                active.setBotId(bot.getId());
            }
            active.setEncryptedToken(operation.getEncryptedToken());
            active.setEncryptedWebhookSecret(operation.getEncryptedWebhookSecret());
            active.setTokenKeyId(operation.getTokenKeyId());
            active.setWebhookKeyId(operation.getWebhookKeyId());
            active.setOperationId(operationId);
            active.setStatus("ACTIVE");
            credentials.saveAndFlush(active);
            bot.setTelegramBotId(operation.getTelegramBotId());
            bot.setTelegramUsername(operation.getTelegramUsername());
            bots.saveAndFlush(bot);
        }
        operation.setFailureCode(null);
        operation.setState(CredentialOperationState.COMPLETED);
        operations.saveAndFlush(operation);
        return result(bot);
    }

    @Transactional
    public void fail(UUID operationId, String failureCode) {
        BotCredentialOperationEntity operation = operations.findByIdForUpdate(operationId).orElse(null);
        if (operation == null || operation.getState() == CredentialOperationState.COMPLETED) {
            return;
        }
        operation.setFailureCode(failureCode == null ? "ONBOARDING_FAILED" : failureCode);
        operation.setState(CredentialOperationState.FAILED);
        operations.saveAndFlush(operation);
    }

    @Transactional(readOnly = true)
    public ActiveCredential prepareRemoval(UUID userId, UUID botId) {
        BotEntity bot = bots.findAccessibleByIdAndUserId(botId, userId).orElseThrow(this::notFound);
        access.requireOwner(userId, bot.getWorkspaceId());
        ActiveCredential active = activeSnapshot(botId);
        if (active == null) {
            throw notFound();
        }
        return active;
    }

    @Transactional
    public BotApplicationService.BotCredentialResult finishRemoval(UUID userId, UUID botId) {
        BotEntity bot = accessibleBotForUpdate(botId, userId);
        access.requireOwner(userId, bot.getWorkspaceId());
        credentials.deleteById(botId);
        bot.setTelegramBotId(null);
        bot.setTelegramUsername(null);
        bots.saveAndFlush(bot);
        return result(bot);
    }

    private BotCredentialOperationEntity operationForUpdate(UUID operationId) {
        return operations.findByIdForUpdate(operationId).orElseThrow(BotCredentialPersistenceService::onboardingConflict);
    }

    private BotEntity accessibleBotForUpdate(UUID botId, UUID userId) {
        return bots.findAccessibleByIdAndUserIdForUpdate(botId, userId).orElseThrow(this::notFound);
    }

    private OnboardingAttempt attempt(BotCredentialOperationEntity operation, ActiveCredential previous) {
        return new OnboardingAttempt(operation.getId(), operation.getBotId(), operation.getOperationType(),
                operation.getState(), decrypt(operation.getEncryptedToken(), tokenAad(operation.getBotId())),
                decrypt(operation.getEncryptedWebhookSecret(), secretAad(operation.getBotId())),
                operation.getTelegramBotId(), operation.getTelegramUsername(), previous);
    }

    private ActiveCredential activeSnapshot(UUID botId) {
        return credentials.findById(botId).map(this::activeSnapshot).orElse(null);
    }

    private ActiveCredential activeSnapshot(BotCredentialEntity credential) {
        if (credential == null) {
            return null;
        }
        return new ActiveCredential(credential.getBotId(),
                decrypt(credential.getEncryptedToken(), tokenAad(credential.getBotId())),
                decrypt(credential.getEncryptedWebhookSecret(), secretAad(credential.getBotId())));
    }

    private BotCredentialHistoryEntity replaced(BotCredentialEntity active, UUID operationId) {
        BotCredentialHistoryEntity previous = new BotCredentialHistoryEntity();
        previous.setId(UUID.randomUUID());
        previous.setBotId(active.getBotId());
        previous.setOperationId(operationId);
        previous.setEncryptedToken(active.getEncryptedToken());
        previous.setEncryptedWebhookSecret(active.getEncryptedWebhookSecret());
        previous.setTokenKeyId(active.getTokenKeyId());
        previous.setWebhookKeyId(active.getWebhookKeyId());
        previous.setState("REPLACED");
        previous.setReplacedAt(Instant.now());
        return previous;
    }

    private BotApplicationService.BotCredentialResult result(BotEntity bot) {
        return new BotApplicationService.BotCredentialResult(BotApplicationService.viewOf(bot));
    }

    private String decrypt(String envelope, String aad) {
        try {
            return cipher.decrypt(envelope, aad);
        } catch (IllegalStateException exception) {
            throw new SafeApiException(503, "CREDENTIAL_UNAVAILABLE", "Stored bot credential is unavailable");
        }
    }

    private static String tokenAad(UUID botId) {
        return botId + ":telegram-token";
    }

    private static String secretAad(UUID botId) {
        return botId + ":webhook-secret";
    }

    private SafeApiException notFound() {
        return new SafeApiException(404, "NOT_FOUND", "Resource was not found");
    }

    private static SafeApiException onboardingConflict() {
        return new SafeApiException(409, "ONBOARDING_CONFLICT", "Bot credential onboarding state has changed");
    }

    public record OnboardingAttempt(UUID operationId, UUID botId, CredentialOperationType operationType,
                                    CredentialOperationState state, String token, String webhookSecret,
                                    Long telegramBotId, String telegramUsername, ActiveCredential previous) {
        @Override
        public String toString() {
            return "OnboardingAttempt[operationId=" + operationId + ", botId=" + botId
                    + ", operationType=" + operationType + ", state=" + state + "]";
        }
    }

    public record ActiveCredential(UUID botId, String token, String webhookSecret) {
        @Override
        public String toString() {
            return "ActiveCredential[botId=" + botId + "]";
        }
    }
}

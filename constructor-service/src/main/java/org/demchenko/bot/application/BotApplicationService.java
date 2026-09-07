package org.demchenko.bot.application;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.application.error.SafeApiException;
import org.demchenko.auth.application.TenantAccessPolicy;
import org.demchenko.bot.data.BotIdempotencyRepository;
import org.demchenko.bot.data.BotRepository;
import org.demchenko.bot.model.BotEntity;
import org.demchenko.bot.model.CredentialOperationState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BotApplicationService {
    private static final List<String> ALLOWED_UPDATES = List.of("message", "callback_query");

    private final BotRepository bots;
    private final BotIdempotencyRepository idempotencies;
    private final TenantAccessPolicy access;
    private final TelegramBotGateway telegram;
    private final BotCredentialPersistenceService credentialPersistence;
    private final BotCreateTransaction botCreateTransaction;

    public BotView create(UUID userId, UUID workspaceId, String key, String name) {
        access.requireOwner(userId, workspaceId);
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new SafeApiException(400, "VALIDATION_ERROR", "Invalid idempotency key");
        }
        String fingerprint = hash(name);
        try {
            return viewOf(botCreateTransaction.create(workspaceId, key, fingerprint, name));
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            var winner = idempotencies.findByWorkspaceIdAndIdempotencyKey(workspaceId, key)
                    .orElseThrow(() -> race);
            if (!winner.getRequestFingerprint().equals(fingerprint)) {
                throw new SafeApiException(409, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used");
            }
            return viewOf(bots.findByIdAndWorkspaceId(winner.getBotId(), workspaceId).orElseThrow(() -> race));
        }
    }

    @Transactional(readOnly = true)
    public List<BotView> list(UUID userId, UUID workspaceId) {
        access.requireOwner(userId, workspaceId);
        return bots.findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId).stream()
                .map(BotApplicationService::viewOf).toList();
    }

    @Transactional(readOnly = true)
    public BotView get(UUID userId, UUID botId) {
        BotEntity bot = bots.findAccessibleByIdAndUserId(botId, userId).orElseThrow(this::notFound);
        access.requireOwner(userId, bot.getWorkspaceId());
        return viewOf(bot);
    }

    @Transactional
    public BotView update(UUID userId, UUID botId, String name) {
        BotEntity bot = bots.findAccessibleByIdAndUserIdForUpdate(botId, userId).orElseThrow(this::notFound);
        access.requireOwner(userId, bot.getWorkspaceId());
        bot.setDisplayName(name);
        return viewOf(bots.save(bot));
    }

    public BotCredentialResult connect(UUID userId, UUID botId, String token, String webhookUrl) {
        BotCredentialPersistenceService.OnboardingAttempt attempt = credentialPersistence.prepareConnect(
                userId, botId, token, generateSecret());
        return execute(attempt, webhookUrl);
    }

    public BotCredentialResult reconnect(UUID userId, UUID botId, String webhookUrl) {
        return execute(credentialPersistence.prepareReconnect(userId, botId), webhookUrl);
    }

    public BotCredentialResult removeCredential(UUID userId, UUID botId) {
        BotCredentialPersistenceService.ActiveCredential active =
                credentialPersistence.prepareRemoval(userId, botId);
        telegram.deleteWebhook(active.token());
        return credentialPersistence.finishRemoval(userId, botId);
    }

    private BotCredentialResult execute(BotCredentialPersistenceService.OnboardingAttempt prepared,
                                        String webhookUrl) {
        BotCredentialPersistenceService.OnboardingAttempt attempt = prepared;
        try {
            if (attempt.state() == CredentialOperationState.PENDING_VERIFICATION) {
                TelegramBotGateway.VerifiedBot verified = telegram.getMe(attempt.token());
                attempt = credentialPersistence.markVerified(attempt.operationId(), verified);
            }
            telegram.setWebhook(attempt.token(), webhookUrl, attempt.webhookSecret(), ALLOWED_UPDATES);
            try {
                return credentialPersistence.activate(attempt.operationId());
            } catch (RuntimeException databaseFailure) {
                restoreRemoteWebhook(attempt, webhookUrl);
                markFailedSafely(attempt.operationId(), "PERSISTENCE_FAILURE");
                throw databaseFailure;
            }
        } catch (RuntimeException failure) {
            markFailedSafely(attempt.operationId(), safeCode(failure));
            throw failure;
        }
    }

    private void markFailedSafely(UUID operationId, String failureCode) {
        try {
            credentialPersistence.fail(operationId, failureCode);
        } catch (RuntimeException ignored) {
            // Preserve the safe primary failure; the persisted pending state remains recoverable.
        }
    }

    private void restoreRemoteWebhook(BotCredentialPersistenceService.OnboardingAttempt attempt,
                                      String webhookUrl) {
        try {
            if (attempt.previous() == null) {
                telegram.deleteWebhook(attempt.token());
            } else {
                telegram.setWebhook(attempt.previous().token(), webhookUrl,
                        attempt.previous().webhookSecret(), ALLOWED_UPDATES);
            }
        } catch (RuntimeException ignored) {
            // The persisted operation is failed/retryable; never replace the safe original error.
        }
    }

    private static String safeCode(RuntimeException failure) {
        return failure instanceof SafeApiException safe ? safe.code() : "ONBOARDING_FAILED";
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String generateSecret() {
        byte[] value = new byte[32];
        new java.security.SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private SafeApiException notFound() {
        return new SafeApiException(404, "NOT_FOUND", "Resource was not found");
    }

    public record BotCredentialResult(BotView bot) {
    }

    public record BotView(UUID id, UUID workspaceId, String displayName, Long telegramBotId,
                          String telegramUsername, Instant createdAt, Instant updatedAt) {
    }

    static BotView viewOf(BotEntity bot) {
        return new BotView(bot.getId(), bot.getWorkspaceId(), bot.getDisplayName(),
                bot.getTelegramBotId(), bot.getTelegramUsername(), bot.getCreatedAt(), bot.getUpdatedAt());
    }
}

package org.demchenko.bot.web;

import lombok.RequiredArgsConstructor;
import org.demchenko.api.generated.api.BotsApi;
import org.demchenko.api.generated.api.CredentialsApi;
import org.demchenko.api.generated.model.*;
import org.demchenko.auth.security.CurrentUserProvider;
import org.demchenko.bot.application.BotApplicationService;
import org.demchenko.bot.application.BotApplicationService.BotView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController @RequiredArgsConstructor @ConditionalOnProperty(prefix="spring.datasource", name="url")
public class BotController implements BotsApi, CredentialsApi {
 private final BotApplicationService service; private final CurrentUserProvider current; private final org.demchenko.bot.config.BotWebhookProperties webhook;
 public ResponseEntity<BotResponse> createBot(String key, UUID workspaceId, CreateBotRequest req){ return ResponseEntity.status(201).body(map(service.create(current.requireUserId(),workspaceId,key,req.getDisplayName()))); }
 public ResponseEntity<BotListResponse> listBots(UUID workspaceId){ return ResponseEntity.ok(new BotListResponse(service.list(current.requireUserId(),workspaceId).stream().map(this::map).toList())); }
 public ResponseEntity<BotResponse> getBot(UUID botId){ return ResponseEntity.ok(map(service.get(current.requireUserId(),botId))); }
 public ResponseEntity<BotResponse> updateBot(UUID botId,UpdateBotRequest req){ return ResponseEntity.ok(map(service.update(current.requireUserId(),botId,req.getDisplayName()))); }
 public ResponseEntity<BotRuntimeStatusResponse> getBotRuntimeStatus(UUID botId){ throw new org.demchenko.api.application.error.SafeApiException(501,"NOT_IMPLEMENTED","Operation is not available"); }
 public ResponseEntity<CredentialStatusResponse> setBotCredential(UUID botId,TelegramCredentialRequest req){ var b=service.connect(current.requireUserId(),botId,req.getToken(),webhook.baseUrl().toString().replaceAll("/$","")+"/"+botId); return ResponseEntity.ok(new CredentialStatusResponse(CredentialStatus.VERIFIED).telegramBotId(b.bot().telegramBotId()).telegramUsername(b.bot().telegramUsername())); }
 public ResponseEntity<CredentialStatusResponse> removeBotCredential(UUID botId){ service.removeCredential(current.requireUserId(),botId); return ResponseEntity.ok(new CredentialStatusResponse(CredentialStatus.DISCONNECTED)); }
 public ResponseEntity<CredentialStatusResponse> reconnectBotCredential(UUID botId){ var b=service.reconnect(current.requireUserId(),botId,webhook.baseUrl().toString().replaceAll("/$","")+"/"+botId); return ResponseEntity.ok(new CredentialStatusResponse(CredentialStatus.VERIFIED).telegramBotId(b.bot().telegramBotId()).telegramUsername(b.bot().telegramUsername())); }
 private BotResponse map(BotView b){ BotStatus s=b.telegramBotId()==null?BotStatus.PENDING_CREDENTIAL:BotStatus.CONNECTED; return new BotResponse(b.id(),b.workspaceId(),b.displayName(),s,OffsetDateTime.ofInstant(b.createdAt(), ZoneOffset.UTC),OffsetDateTime.ofInstant(b.updatedAt(), ZoneOffset.UTC)).telegramBotId(b.telegramBotId()).telegramUsername(b.telegramUsername()); }
}

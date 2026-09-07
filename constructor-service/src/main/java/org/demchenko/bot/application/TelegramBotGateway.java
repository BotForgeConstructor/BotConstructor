package org.demchenko.bot.application;
import java.util.List;
public interface TelegramBotGateway {
    VerifiedBot getMe(String token);
    void setWebhook(String token, String url, String secret, List<String> allowedUpdates);
    void deleteWebhook(String token);
    record VerifiedBot(long id, String username) {}
}

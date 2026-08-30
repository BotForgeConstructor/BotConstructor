package org.demchenko.telegram.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.demchenko.telegram.dispetcher.BotUpdateDispatcher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
@Slf4j
public class TelegramBotConnection extends TelegramLongPollingBot {

    private final BotUpdateDispatcher dispatcher;
    private final ManagementTelegramProperties properties;

    public TelegramBotConnection(BotUpdateDispatcher dispatcher, ManagementTelegramProperties properties) {
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @Override
    public void onUpdateReceived(Update update) {
        dispatcher.dispatch(update);
    }

    @Override
    public String getBotUsername() {
        return properties.username();
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            log.info("Bot successfully registered!");
        } catch (Exception e) {
            log.error("Error while registering bot", e);
        }
    }
}

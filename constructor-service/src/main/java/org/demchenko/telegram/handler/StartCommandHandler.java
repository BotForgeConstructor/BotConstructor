package org.demchenko.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.telegram.service.impl.TelegramInlineKeyboardService;
import org.demchenko.telegram.service.impl.TelegramMessageService;
import org.demchenko.telegram.config.ManagementMiniAppProperties;
import org.demchenko.telegram.service.input.AbstractCommandInputHandler;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.telegram.management", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StartCommandHandler extends AbstractCommandInputHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;
    private final ManagementMiniAppProperties miniApp;

    @Override
    protected String getCommandText() {
        return "start";
    }

    @Override
    protected void processCommand(Update update) {
        WebAppInlineKeyboardButton button = new WebAppInlineKeyboardButton(miniApp.buttonLabel(), miniApp.url().toString());
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(List.of(List.of(button))).build();
        telegramMessageService.sendMessage(
                update.getMessage().getChatId(),
                "Open Bot Constructor:", keyboard
        );
    }
}

package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.service.impl.TelegramInlineKeyboardService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.input.AbstractCommandInputHandler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StartCommandHandler extends AbstractCommandInputHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;

    @Override
    protected String getCommandText() {
        return "start";
    }

    @Override
    protected void processCommand(Update update) {
        telegramMessageService.sendMessage(
                update.getMessage().getChatId(),
                "Chose function for your bot:",
                telegramInlineKeyboardService.buildButton(
                        List.of("Create", "Edit"),
                        List.of("CREATE:start", "EDIT:start")
                )
        );
    }
}

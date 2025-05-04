package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.service.impl.TelegramButtonService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.input.AbstractCommandInputService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StartCommandHandler extends AbstractCommandInputService {

    private final TelegramMessageService telegramMessageService;
    private final TelegramButtonService telegramButtonService;

    @Override
    protected String getCommandText() {
        return "start";
    }

    @Override
    protected void processCommand(Update update) {
        telegramMessageService.sendMessage(
                update.getMessage().getChatId(),
                "Chose function for your bot:",
                telegramButtonService.buildButton(
                        List.of("Create", "Edit"),
                        List.of("START_MENU:START", "START_MENU:EDIT")
                )
        );
    }
}

package org.demchenko.telegram.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.telegram.config.TelegramMediaProperties;
import org.demchenko.telegram.service.impl.TelegramInlineKeyboardService;
import org.demchenko.telegram.service.impl.TelegramMessageService;
import org.demchenko.telegram.service.input.AbstractInlineKeyboardHandler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InstructionGifsBtnHandler extends AbstractInlineKeyboardHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;
    private final TelegramMediaProperties mediaProperties;

    @Override
    protected String getPrefix() {
        return "GIF";
    }


    @Override
    protected void processCallback(Update update, String[] params) {
        telegramMessageService.sendAnimation(update.getCallbackQuery().getMessage().getChatId(),
                telegramInlineKeyboardService.buildButton(
                        List.of("Next step", "Previous step","Back"),
                        List.of("NEXT:GIF:CREATE", "PREVIOUS:GIF:CREATE", "BACK:GIF:CREATE")),
                mediaProperties.lessonPath()
                ,"output.gif");
    }
}

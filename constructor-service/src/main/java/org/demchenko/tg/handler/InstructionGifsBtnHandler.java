package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.service.impl.TelegramInlineKeyboardService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.input.AbstractInlineKeyboardHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InstructionGifsBtnHandler extends AbstractInlineKeyboardHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;

    @Value("${video.lesson.dir}")
    private String VIDEO_LESSON_DIR;

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
                VIDEO_LESSON_DIR
                ,"output.gif");
    }
}

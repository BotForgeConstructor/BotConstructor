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
public class VideoBtnHandler extends AbstractInlineKeyboardHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;

    @Value("${video.lesson.dir}")
    private String VIDEO_LESSON_DIR;

    @Override
    protected String getPrefix() {
        return "VIDEO";
    }

    //todo: test this handler
    @Override
    protected void processCallback(Update update, String[] params) { // todo: separate Lesson and MAIN_MENU logics for callback
        telegramMessageService.sendAnimation(update.getCallbackQuery().getMessage().getChatId(),
                telegramInlineKeyboardService.buildButton(
                        List.of("Back"),
                        List.of("BACK:VIDEO")),
                VIDEO_LESSON_DIR);
    }
}

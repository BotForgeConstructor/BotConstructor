package org.demchenko.tg.handler;

import lombok.RequiredArgsConstructor;
import org.demchenko.tg.service.impl.TelegramInlineKeyboardService;
import org.demchenko.tg.service.impl.TelegramMessageService;
import org.demchenko.tg.service.input.AbstractInlineKeyboardHandler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SlideBtnHandler extends AbstractInlineKeyboardHandler {

    private final TelegramMessageService telegramMessageService;
    private final TelegramInlineKeyboardService telegramInlineKeyboardService;

    @Override
    protected String getPrefix() {
        return "SLIDE";
    }

    @Override
    protected void processCallback(Update update, String[] params) {
        telegramMessageService.sendMessage(update.getCallbackQuery().getFrom().getId()
                ,"MarkdownV2"
                ,"[Instruction link](https://telegra.ph/Token-create-instruction-05-12)"
                ,telegramInlineKeyboardService.buildButton(
                        List.of("Back"),
                        List.of("BACK:SLIDE:CREATE")
                )
        );
    }
}
